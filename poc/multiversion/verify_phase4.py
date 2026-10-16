"""Independent checks over retained production evidence, never launches Minecraft."""
import argparse, base64, collections, gzip, json, re
from pathlib import Path
from run_phase2 import ROOT

def canonical(value):
    if isinstance(value,dict):return {k:'<ISO8601>' if k=='active' else canonical(v) for k,v in value.items()}
    if isinstance(value,list):return sorted([canonical(v) for v in value],key=lambda x:json.dumps(x,sort_keys=True,ensure_ascii=False))
    return value

def wire_checks(records,mode):
    checks={}
    http=[r for r in records if r['kind']=='http' and r['headers'].get('User-Agent')=='Phase4Consumer/1']
    checks['GET_headers']=all(r['headers'].get('X-API-Key')=='phase4-local-key' for r in http)
    checks['query_encoding']=any(['probe','Игрок &?+'] in r.get('query',[]) for r in http)
    checks['captcha_REST']=any(r['kind']=='post' and r['type']=='captcha' and r['payload']=={'base64':'cGhhc2U0'} for r in records)
    post=[r for r in http if r['method']=='POST']
    checks['POST_content_type']=bool(post) and all(r['headers'].get('Content-Type')=='application/json' for r in post)
    checks['gzip_wire']=all((r['headers'].get('Content-Encoding')=='gzip')==(mode!='plain')
        and (base64.b64decode(r['raw_base64'])[:2]==b'\x1f\x8b')==(mode!='plain') for r in post)
    for r in post:
        data=base64.b64decode(r['raw_base64']);data=gzip.decompress(data) if mode!='plain' else data
        json.loads(data)
    if mode=='ws':
        ws=[r for r in records if r['kind']=='ws']
        checks['WS_text_only']=bool(ws) and all(r['frame']=='TEXT' for r in ws)
        checks['WS_auth']=any(r['envelope']=={'type':'auth','api_key':'phase4-local-key'} for r in ws)
        checks['WS_all_7_types']={r['envelope']['type'] for r in ws}-{'auth'}=={'players','bans','copper_dungeon','warden_city','hell_map','players_around','coordinates'}
        checks['WS_envelopes']=all(set(r['envelope'])=={'type','body'} for r in ws if r['envelope']['type']!='auth')
        checks['captcha_only_HTTP_POST']=len(post)==1
    else:
        sse=[r for r in records if r['kind']=='sse_open']
        checks['SSE_five_streams']=len({(r['path'],json.dumps(r['query'],sort_keys=True)) for r in sse})==5
        checks['SSE_headers']=bool(sse) and all(r['headers'].get('Accept')=='text/event-stream' and r['headers'].get('X-API-Key')=='phase4-local-key' for r in sse)
        checks['REST_all_7_types']={r['type'] for r in records if r['kind']=='post'}-{'captcha'}=={'tab','ban','copper','warden','hell','mine','coordinates'}
    return checks

def main():
    p=argparse.ArgumentParser();p.add_argument('--stage',choices=['a','b','c'],required=True);p.add_argument('--version');a=p.parse_args()
    stage=a.stage;version=a.version or {'a':'0.0.7-phase4a.2','b':'0.0.8-phase4b','c':'0.0.9-phase4c'}[stage]
    fixtures=json.loads((ROOT/'fixtures/data/phase3/tracker-sessions.json').read_text(encoding='utf-8'))
    result={'stage':'4'+stage.upper(),'version':version,'targets':{},'native_status':'OPEN','failed_attempts':[]}
    for mc,java,namespace in [('1.21.4',21,'intermediary'),('26.3',25,'official')]:
        parent=ROOT/'results/phase4'/('4'+stage.upper())/mc;runs=[]
        for path in sorted(parent.glob('attempt-*/result.json')):
            run=json.loads(path.read_text(encoding='utf-8'))
            if run['status']!='PASS':
                log=(path.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
                result['failed_attempts'].append({'path':str(path.relative_to(ROOT)),'error':run.get('error'),
                    'native_codes':sorted(set(re.findall(r'NTSTATUS (0xC000[0-9A-Fa-f]{4})',log)))})
                continue
            rt=run['runtime']
            if rt['sdk_version']!=version:continue
            checks={'production':not rt['development'] and rt['origin']=='NESTED',
                'java_namespace':rt['java']==java and rt['namespace']==namespace,
                'JOIN_DISCONNECT':rt['joins']==21 and rt['disconnects']==21,
                'state_reset':all(s['state_cleared'] and s['disconnect_handled']=='Render thread' for s in rt['sessions']),
                'handoff_empty':rt['handoff_empty'],'callbacks':len(rt['callbacks'])==40,
                'scheduler':rt['periodic']==9,'normal_exits':run['client_exit']==0 and run['server_exit']==0}
            for i,f in enumerate(fixtures):
                expected=json.loads(json.dumps(f['expected'])) if stage!='a' else {k:[] for k in f['expected']}
                if run.get('individual_flags'):
                    disabled={1:'ban',2:'coordinates',3:'warden',4:'mine',5:'tab',7:'hell'}.get(i+1)
                    if disabled:expected[disabled]=[]
                checks['golden_'+f['name']]=canonical(rt['sessions'][i]['payloads'])==canonical(expected)
            if stage!='a':
                checks['public_GET_Captcha_caches']=rt['public_network']['all_public_requests'] and rt['public_network']['all_public_caches']
                backend=ROOT/run['backend_evidence'].replace('\\','/')/'raw.jsonl'
                records=[json.loads(l) for l in backend.read_text(encoding='utf-8').splitlines()]
                wire=wire_checks(records,run['mode'])
                if run.get('individual_flags'):
                    wire.pop('REST_all_7_types',None);wire.pop('WS_all_7_types',None);wire.pop('captcha_only_HTTP_POST',None)
                    checks['individual_flag_matrix']=len(rt['payloads'])==16 and rt['sessions'][2]['entity_visits']==0
                checks.update(wire)
                checks['two_mods_one_SDK']='[PHASE4A] PEER_MOD_PASS' in (path.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
                if stage=='c':
                    checks['online_init_rollback']=rt['public_network'].get('online_initialization_rollback',False)
                    checks['production_shutdown_threads']=rt.get('shutdown_threads_empty',False)
            runs.append({'path':str(path.relative_to(ROOT)),'mode':run.get('mode','offline'),'individual_flags':run.get('individual_flags',False),'checks':checks,
                'status':'PASS' if all(checks.values()) else 'FAIL'})
        required={'offline'} if stage=='a' else {'ws','rest'}
        observed={r['mode'] for r in runs if r['status']=='PASS' and not r['individual_flags']}
        flag_pass=any(r['status']=='PASS' and r['individual_flags'] for r in runs)
        result['targets'][mc]={'runs':runs,'required_modes':sorted(required),'individual_flags_pass':flag_pass,
            'status':'PASS' if required<=observed and (stage!='c' or flag_pass) else 'FAIL'}
    result['status']='FUNCTIONAL_PASS_NATIVE_OPEN' if all(v['status']=='PASS' for v in result['targets'].values()) else 'FAIL'
    path=ROOT/'results/phase4'/('4'+stage.upper())/'summary.json';path.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for mc,t in result['targets'].items():
        print(mc,t['status'])
        for r in t['runs']:print(r['mode'],r['status'],[k for k,v in r['checks'].items() if not v])
    print(result['status']);raise SystemExit(result['status']=='FAIL')
if __name__=='__main__':main()
