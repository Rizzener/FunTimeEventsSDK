"""Fail-closed verification of Phase 5 evidence; absent jobs are NOT VERIFIED."""
import argparse,base64,gzip,hashlib,io,json,os,re,zipfile
from pathlib import Path
import verify_artifacts as packaging
from run_phase2 import ROOT,maven_local
from verify_phase4 import canonical,wire_checks

CONFIG=json.loads((ROOT/'targets.json').read_text())
PREFIX=Path(os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6'))
def load(path):return json.loads(path.read_text(encoding='utf-8'))
def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def headless_checks(display,private=False):
    checks={'verified_headless_X11':display.get('status')=='PASS' and display.get('checks',{}).get('xdpyinfo_real_X11_server') is True}
    if private:
        required=['xdpyinfo_real_X11_server','private_socket','private_mount_namespace','real_xkbcomp_available','no_WSLg_fallback']
        checks['private_Xvfb_without_WSLg']=display.get('kind')=='private-xvfb' and all(display.get('checks',{}).get(k) is True for k in required)
    return checks
def canonical_game(value):
    # Entity/player collections are unordered; numeric coordinate vectors are not.
    if isinstance(value,dict):return {k:'<ISO8601>' if k=='active' else canonical_game(v) for k,v in value.items()}
    if isinstance(value,list):
        items=[canonical_game(v) for v in value]
        return items if all(isinstance(v,(int,float)) for v in value) else sorted(items,key=lambda v:json.dumps(v,sort_keys=True,ensure_ascii=False))
    return value
assert canonical_game([-123,64,456])!=canonical_game([-123,456,64])
def successful(mc,kind):
    return [(p,load(p)) for p in sorted((ROOT/PREFIX/mc/kind).glob('attempt-*/result.json')) if load(p).get('status')=='PASS']

def artifacts(mc):
    spec=next(t for t in CONFIG['targets'] if t['minecraft']==mc)
    packaging.VERSION=CONFIG['sdk_version'];packaging.CHECKS=[]
    packaging.inspect(mc,spec['java'],maven_local())
    checks={c['check']:c['status']=='PASS' for c in packaging.CHECKS}
    version=CONFIG['sdk_version'];stem='mc'+mc+'-'+version
    folder=maven_local()/'com/github/Rizzener/FunTimeEventsSDK'/('mc'+mc)/version
    hashes={}
    for suffix in ['.jar','-sources.jar','.pom','.module']:
        p=folder/(stem+suffix);other=ROOT/'build/staging-maven/com/github/Rizzener/FunTimeEventsSDK'/('mc'+mc)/version/p.name
        checks['test_repository_equal_'+suffix]=p.exists() and other.exists() and digest(p)==digest(other)
        if p.exists():hashes[p.name]=digest(p)
    jar=folder/(stem+'.jar')
    if jar.exists():
        with zipfile.ZipFile(jar) as z:
            names=z.namelist();core=[str(p.relative_to(ROOT/'core/src/main/java')).replace('\\','/')[:-5]+'.class' for p in (ROOT/'core/src/main/java').rglob('*.java')]
            checks['every_core_class_single_Java21']=all(names.count(n)==1 and packaging.major(z.read(n))==65 for n in core)
            checks['all_core_independent']=all(b'net/minecraft/' not in z.read(n) and b'net/fabricmc/' not in z.read(n) for n in core)
            classes=[n for n in names if n.endswith('.class')]
            checks['all_classes_production_namespace']=all(not re.search(b'net/minecraft/(?!class_)[A-Za-z]',z.read(n)) for n in classes) if spec['java']==21 else all(b'net/minecraft/class_' not in z.read(n) for n in classes)
            manifest=z.read('META-INF/MANIFEST.MF').decode()
            checks['manifest_versions']=all(k+': '+v in manifest for k,v in [('Fabric-Gradle-Version',CONFIG['gradle']),('Fabric-Loom-Version',CONFIG['loom']),('Fabric-Minecraft-Version',mc)])
            checks['legacy_manifest_namespace']=spec['java']!=21 or 'Fabric-Mapping-Namespace: intermediary' in manifest
            checks['real_public_API']=all(n in names for n in ['net/funtimeevents/api/FunTimeEventsAPI.class','net/funtimeevents/api/FteConfig.class'])
            mixin=load_mixin=json.loads(z.read('fte-trackers.mixins.json'))
            checks['both_accessor_mixins']=len(mixin.get('client',[]))==2
            checks['no_service_loader']=not any(n.startswith('META-INF/services/') for n in names)
        with zipfile.ZipFile(folder/(stem+'-sources.jar')) as z:
            checks['real_sources']=all(n in z.namelist() for n in ['net/funtimeevents/api/FunTimeEventsAPI.java','net/funtimeevents/api/FteConfig.java','net/funtimeevents/tracker/dungeon/DungeonTracker.java'])
    result={'status':'PASS' if checks and all(checks.values()) else 'FAIL','checks':checks,'sha256':hashes,'packaging':packaging.CHECKS}
    save(ROOT/PREFIX/mc/'artifacts.json',result);return result

def network(mc):
    runs=successful(mc,'network')
    if not runs:return {'status':'NOT VERIFIED'}
    path,result=runs[-1];out=path.parent;records=[json.loads(l) for l in (out/'raw.jsonl').read_text(encoding='utf-8').splitlines()]
    checks={};profiles={}
    for label in ['legacy','ported-'+mc]:
        profile={}
        for role,prefix in [('gzip','Parity/'),('plain','ParityPlain/')]:
            posts=[r for r in records if r['kind']=='post' and r.get('user_agent')==prefix+label]
            profile[role]=sorted([{'path':r['path'],'payload':r['payload']} for r in posts],key=lambda r:r['path'])
            checks[label+role+'_8_posts']=len(posts)==8 and {r['type'] for r in posts}=={'tab','ban','copper','warden','hell','mine','coordinates','captcha'}
            raw=[r for r in records if r['kind']=='http' and r['method']=='POST' and r['headers'].get('User-Agent')==prefix+label]
            checks[label+role+'_headers_bytes']=len(raw)==8 and all(r['headers'].get('Content-Type')=='application/json' and r['headers'].get('X-API-Key')=='phase4-local-key' and (r['headers'].get('Content-Encoding')=='gzip')==(role=='gzip') and (base64.b64decode(r['raw_base64'])[:2]==b'\x1f\x8b')==(role=='gzip') for r in raw)
            for r in raw:
                b=base64.b64decode(r['raw_base64']);json.loads(gzip.decompress(b) if role=='gzip' else b)
        ws=[r for r in records if r['kind']=='ws' and r.get('user_agent')=='Parity/'+label]
        checks[label+'_WS_auth_text']=len(ws)==8 and all(r['frame']=='TEXT' for r in ws) and any(r['envelope']=={'type':'auth','api_key':'phase4-local-key'} for r in ws)
        profile['ws']=sorted([r['envelope'] for r in ws if r['envelope']['type']!='auth'],key=lambda e:e['type']);profiles[label]=profile
    checks['old_SDK_wire_equal']=profiles['legacy']==profiles['ported-'+mc]
    hardened=load(out/'hardening.json');checks['hardening_10']=hardened['status']=='PASS' and len(hardened['checks'])==10 and all(hardened['checks'].values())
    rechecks=successful(mc,'hardening')
    checked_out=rechecks[-1][0].parent if rechecks else out
    corrected=load(checked_out/'hardening.json')
    checks['future_counter_negative_control']='FUTURE_ACCOUNTING_PASS root_done_before_callback=true negative_overlap_detected=2' in (checked_out/'java.txt').read_text(encoding='utf-8',errors='replace')
    checks['corrected_hardening_10']=corrected['status']=='PASS' and len(corrected['checks'])==10 and all(corrected['checks'].values())
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'evidence':str(path.relative_to(ROOT)),'profiles':profiles}
    save(ROOT/PREFIX/mc/'network.json',result);return result

def runtime(mc):
    spec=next(t for t in CONFIG['targets'] if t['minecraft']==mc)
    fixtures=load(ROOT/'fixtures/data/phase3/tracker-sessions.json');modes={}
    for mode in ['offline','rest','ws','flags']:
        runs=[]
        for path,run in successful(mc,'runtime-'+mode):
            rt=run['runtime']
            if rt['sdk_version']!=CONFIG['sdk_version']:continue
            checks={'production':not rt['development'] and rt['origin']=='NESTED','java_namespace':rt['java']==spec['java'] and rt['namespace']==spec['namespace'],
                'JOIN_DISCONNECT':rt['joins']==21 and rt['disconnects']==21,'state_reset':all(s['state_cleared'] and s['disconnect_handled']=='Render thread' for s in rt['sessions']),
                'handoff_empty':rt['handoff_empty'],'callbacks_40':len(rt['callbacks'])==40,'scheduler_9':rt['periodic']==9,'normal_exits':run['client_exit']==0 and run['server_exit']==0}
            if run.get('runner_os','').startswith('linux') or 'wsl' in PREFIX.parts or 'github' in PREFIX.parts:
                checks.update(headless_checks(run.get('display_receipt',{}),'wsl' in PREFIX.parts))
                if mc=='26.3':
                    game_log=path.parent/'game-logs/latest.log'
                    checks['actual_SDL_X11_window']=game_log.exists() and 'Created window using SDL video driver: x11' in game_log.read_text(encoding='utf-8',errors='replace')
            for i,f in enumerate(fixtures):
                expected=json.loads(json.dumps(f['expected'])) if mode!='offline' else {k:[] for k in f['expected']}
                if mode=='flags':
                    disabled={1:'ban',2:'coordinates',3:'warden',4:'mine',5:'tab',7:'hell'}.get(i+1)
                    if disabled:expected[disabled]=[]
                checks['golden_'+f['name']]=canonical_game(rt['sessions'][i]['payloads'])==canonical_game(expected)
            if mode!='offline':
                checks['public_GET_Captcha_caches']=rt['public_network']['all_public_requests'] and rt['public_network']['all_public_caches']
                raw=ROOT/run['backend_evidence'].replace('\\','/')/'raw.jsonl';records=[json.loads(l) for l in raw.read_text(encoding='utf-8').splitlines()]
                wire=wire_checks(records,'rest' if mode=='flags' else mode)
                if mode=='flags':
                    wire.pop('REST_all_7_types',None);wire.pop('WS_all_7_types',None);wire.pop('captcha_only_HTTP_POST',None)
                    checks['disable_flags']=len(rt['payloads'])==16 and rt['sessions'][2]['entity_visits']==0
                checks.update(wire)
                checks['online_init_rollback']=rt['public_network'].get('online_initialization_rollback',False)
                checks['shutdown_threads_empty']=rt.get('shutdown_threads_empty',False)
            checks['two_mods_old_binary']='[PHASE4A] PEER_MOD_PASS' in (path.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
            perf=rt['callback_performance']
            checks['old_binary_on_new_only']='[PHASE4A] OLD_BINARY_PASS' in (path.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
            checks['performance_counters']=all(perf.get(k,[0])[0]>0 for k in ['sdk_tick_active','sdk_tick_idle']) and all(rt.get('performance',{}).get(k,[0])[0]>0 for k in ['dungeon','tab','mine','hell'])
            measurements={k:{'count':v[0],'mean_ns':v[1]/v[0],'max_ns':v[2],'mean_bytes':v[3]/v[0],'max_bytes':v[4]} for k,v in perf.items() if v[0]}
            runs.append({'path':str(path.relative_to(ROOT)),'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'tick_and_callback_performance':measurements,
                'tracker_performance':rt.get('performance'),'reads':[{k:s[k] for k in ['name','entity_visits','text_display_reads','block_reads']} for s in rt['sessions']]})
        modes[mode]={'status':'PASS' if any(r['status']=='PASS' for r in runs) else 'NOT VERIFIED' if not runs else 'FAIL','runs':runs}
    result={'status':'PASS' if all(v['status']=='PASS' for v in modes.values()) else 'NOT VERIFIED','modes':modes,'native_status':'OPEN'}
    save(ROOT/PREFIX/mc/'runtime.json',result);return result

def summary():
    result={'native_status':'OPEN','targets':{},'failed_attempts':[]}
    for t in CONFIG['targets']:
        mc=t['minecraft'];row={}
        for kind in ['compile','publish','consumer']:
            row[kind]='PASS' if successful(mc,kind) else 'NOT VERIFIED'
        for kind in ['artifacts','network','runtime','api']:
            p=ROOT/PREFIX/mc/(kind+'.json');row[kind]=load(p)['status'] if p.exists() else 'NOT VERIFIED'
        row['status']='PASS' if all(v=='PASS' for v in row.values()) else 'NOT VERIFIED';result['targets'][mc]=row
    for p in (ROOT/PREFIX).rglob('attempt-*/result.json'):
        r=load(p)
        status=r.get('status')
        if status is None and isinstance(r.get('exit_code'),int):status='PASS' if r['exit_code']==0 else 'FAIL'
        if status!='PASS':result['failed_attempts'].append({'path':str(p.relative_to(ROOT)),'status':status or 'NOT VERIFIED','error':r.get('error')})
    result['status']='FUNCTIONAL_PASS_NATIVE_OPEN' if all(t['status']=='PASS' for t in result['targets'].values()) else 'NOT VERIFIED'
    save(ROOT/PREFIX/'summary.json',result);return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--stage',choices=['artifacts','network','runtime','summary'],required=True);p.add_argument('--targets',default='all');a=p.parse_args()
    targets=[t['minecraft'] for t in CONFIG['targets']] if a.targets=='all' else a.targets.split(',')
    if a.stage=='summary':results=[summary()]
    else:
        results=[]
        for mc in targets:
            try:r=globals()[a.stage](mc)
            except Exception as error:r={'status':'FAIL','error':repr(error)};save(ROOT/PREFIX/mc/(a.stage+'.json'),r)
            results.append(r);print(mc,a.stage,r['status'],[k for k,v in r.get('checks',{}).items() if not v],flush=True)
    raise SystemExit(any(r['status'] not in ['PASS','FUNCTIONAL_PASS_NATIVE_OPEN'] for r in results))
