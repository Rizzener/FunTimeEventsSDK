"""Loopback-only FunTime REST/WS/SSE protocol fixture; raw bytes are retained.

Requires aiohttp (see requirements-phase4.txt). Test keys are never production keys.
"""
import argparse, asyncio, base64, gzip, json, time, ssl
from pathlib import Path
from aiohttp import web, WSMsgType

DATA=Path(__file__).parent/'data/phase4/backend.json'
TYPES={'/players':'tab','/bans':'ban','/copper-dungeon':'copper','/warden-city':'warden',
       '/events/hell-map':'hell','/mines/players-around':'mine','/events/coordinates':'coordinates'}
WS_TYPES={'players':'tab','bans':'ban','copper_dungeon':'copper','warden_city':'warden',
          'hell_map':'hell','players_around':'mine','coordinates':'coordinates'}

class Backend:
    def __init__(self, output):
        self.output=output;output.parent.mkdir(parents=True,exist_ok=True)
        self.records=[];self.session=0;self.fixture=json.loads(DATA.read_text(encoding='utf-8'))
        self.stream_attempts={};self.open_ws=0;self.open_sse=0
        self.late_connections=0;self.late_old=None
    def log(self, **row):
        row={'at':time.time(),'session':self.session,**row};self.records.append(row)
        with self.output.open('a',encoding='utf-8') as f:f.write(json.dumps(row,ensure_ascii=False)+'\n')
    async def handle(self, r):
        path=r.path
        if path=='/__session':
            self.session=int(r.query['n']);self.log(kind='session');return web.json_response({'ok':True})
        if path=='/__records':return web.json_response({'records':self.records,'open_ws':self.open_ws,'open_sse':self.open_sse})
        if path=='/__health':return web.json_response({'ready':True})
        route=path[path.index('/v1')+3:] if '/v1' in path else path
        if route=='/relay':return await self.relay(r)
        if route.endswith('/stream'):return await self.sse(r,route)
        raw=await r.read()
        self.log(kind='http',method=r.method,path=route,query=list(r.query.items()),headers=dict(r.headers),
                 raw_base64=base64.b64encode(raw).decode())
        if '/timeout/' in path:await asyncio.sleep(17)
        if '/malformed/' in path:return web.Response(text='{broken json',content_type='application/json')
        if '/error/' in path:return web.json_response({'error':'fixture failure'},status=503)
        if r.method=='POST':
            try:
                body=json.loads(gzip.decompress(raw) if r.headers.get('Content-Encoding')=='gzip' else raw)
            except Exception as e:return web.json_response({'bad_body':type(e).__name__},status=400)
            self.log(kind='post',path=route,type=TYPES.get(route,'captcha'),payload=body,
                     encoding=r.headers.get('Content-Encoding'),gzip_magic=raw[:2].hex(),user_agent=r.headers.get('User-Agent'))
            return web.json_response(self.fixture['captcha'] if route=='/captcha' else {'ok':True})
        name={'/events':'user_events' if r.query.get('event_type')=='user' else 'events',
              '/mines':'mines','/players':'players','/bans':'bans','/copper-dungeon':'copper_dungeons','/warden-city':'warden_cities'}.get(route)
        return web.json_response(self.fixture[name] if name else {'unknown':route},status=200 if name else 404)
    async def relay(self,r):
        ws=web.WebSocketResponse(autoping=True,compress=False);await ws.prepare(r);self.open_ws+=1
        late_index=0
        if '/late/' in r.path:
            self.late_connections+=1;late_index=self.late_connections
            if late_index==1:self.late_old=ws
        self.log(kind='ws_open',path=r.path,headers=dict(r.headers));snapshot_task=None
        try:
            async for message in ws:
                if message.type==WSMsgType.TEXT:
                    obj=json.loads(message.data);self.log(kind='ws',frame='TEXT',envelope=obj,user_agent=r.headers.get('User-Agent'))
                    if obj.get('type')=='auth':
                        if '/noauth/' in r.path:continue
                        if late_index==1:continue
                        if '/reject/' in r.path or obj.get('api_key')!='phase4-local-key':
                            await ws.close(code=4001,message=b'fixture auth rejected');break
                        await ws.send_str('{ "type" : "auth_ok" }')
                        async def snapshots():
                            if '/silent/' in r.path:return
                            while not ws.closed:
                                snap={'type':'snapshot','ts':time.time(),**{k:self.fixture[k]['data'] for k in
                                    ['events','user_events','mines','copper_dungeons','warden_cities']},'system_info':self.fixture['system_info']}
                                await ws.send_str(json.dumps(snap,ensure_ascii=False));await asyncio.sleep(1)
                        snapshot_task=asyncio.create_task(snapshots())
                        if late_index==2:
                            async def close_old():
                                await asyncio.sleep(1.5)
                                self.log(kind='late_old_close',already_closed=self.late_old.closed)
                                await self.late_old.close(code=4001,message=b'late rejection from previous connection')
                            asyncio.create_task(close_old())
                elif message.type==WSMsgType.BINARY:self.log(kind='ws',frame='BINARY',raw_base64=base64.b64encode(message.data).decode())
        finally:
            if snapshot_task:snapshot_task.cancel()
            self.open_ws-=1;self.log(kind='ws_closed',path=r.path,user_agent=r.headers.get('User-Agent'))
        return ws
    async def sse(self,r,route):
        key=r.path_qs+'|'+r.headers.get('User-Agent','');attempt=self.stream_attempts.get(key,0)+1;self.stream_attempts[key]=attempt
        self.log(kind='sse_open',path=route,query=list(r.query.items()),attempt=attempt,headers=dict(r.headers))
        if '/status/' in r.path and attempt==1:return web.Response(status=503,text='retry fixture')
        response=web.StreamResponse(headers={'Content-Type':'text/event-stream'});await response.prepare(r);self.open_sse+=1
        name={'/events/stream':'user_events' if r.query.get('event_type')=='user' else 'events','/mines/stream':'mines',
              '/copper-dungeon/stream':'copper_dungeons','/warden-city/stream':'warden_cities'}[route]
        try:
            while True:
                if '/hang/' not in r.path:
                    # Both accepted legacy representations, with an SSE comment/heartbeat.
                    value=self.fixture[name] if attempt%2 else self.fixture[name]['data']
                    await response.write((': heartbeat\ndata: '+json.dumps(value,ensure_ascii=False)+'\n\n').encode())
                if '/eof/' in r.path:break
                await asyncio.sleep(0.5)
        except (ConnectionError,asyncio.CancelledError):pass
        finally:self.open_sse-=1;self.log(kind='sse_closed',path=route,attempt=attempt)
        return response

async def serve(args):
    backend=Backend(args.output);app=web.Application();app.router.add_route('*','/{path:.*}',backend.handle)
    runner=web.AppRunner(app,auto_decompress=False,shutdown_timeout=1);await runner.setup()
    await web.TCPSite(runner,'127.0.0.1',args.port).start()
    if args.tls_dir:
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(args.tls_dir/'server-cert.pem',args.tls_dir/'server-key.pem')
        await web.TCPSite(runner,'127.0.0.1',args.port+1,ssl_context=context).start()
    print('FAKE_BACKEND_READY',args.port,flush=True)
    try:await asyncio.Event().wait()
    finally:await runner.cleanup()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--port',type=int,default=29840);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--tls-dir',type=Path)
    asyncio.run(serve(p.parse_args()))
