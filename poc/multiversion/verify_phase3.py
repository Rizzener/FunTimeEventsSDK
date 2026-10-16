"""Independent golden/wire/lifecycle/artifact check, not just a PASS marker check."""
from pathlib import Path
from collections import Counter
import argparse
import datetime
import hashlib
import json
import re
import struct
import zipfile
import phase3_port_spec as spec

ROOT=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--version',default='0.0.6-phase3')
parser.add_argument('--min26',type=int,default=3,help='Required independent production launches of 26.3')
args=parser.parse_args()
VERSION=args.version
fixtures=json.loads((ROOT/'fixtures/data/phase3/tracker-sessions.json').read_text(encoding='utf-8'))


def canonical(value):
    if isinstance(value,list):return sorted([canonical(v) for v in value],key=lambda v:json.dumps(v,ensure_ascii=False,sort_keys=True))
    if isinstance(value,dict):
        result={}
        for k,v in value.items():
            if k=='active':
                if v!='<ISO8601>':datetime.datetime.fromisoformat(v.replace('Z','+00:00'))
                result[k]='<ISO8601>'
            else:result[k]=canonical(v)
        return result
    return value


source_checks={str(target.relative_to(ROOT)):spec.matches(target,expected) for _,target,expected in spec.expected_files()}
summary={'version':VERSION,'source_checks':source_checks,'targets':[]}
for mc,java,namespace in [('1.21.4',21,'intermediary'),('26.3',25,'official')]:
    root=ROOT/'results/phase3'/mc
    attempts=[]
    for p in sorted(root.glob('attempt-*/result.json')):
        row=json.loads(p.read_text(encoding='utf-8'))
        client_text=(p.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
        loaded=re.search(r'fte-multiversion-poc ([\w.\-]+)',client_text)
        native=re.findall(r'NTSTATUS (0x[0-9A-Fa-f]+)',client_text)
        attempts.append({'path':str(p.relative_to(ROOT)),'status':row['status'],'error':row.get('error'),
                         'version':row.get('runtime',{}).get('sdk_version') or (loaded[1] if loaded else None),
                         'native_exit_codes':native,'seconds':row.get('seconds')})
    valid=[a for a in attempts if a['status']=='PASS' and a['version']==VERSION]
    result={'target':mc,'attempts':attempts,'successful_repeats':len(valid),'runs':[],'packaging':{}}
    sdk=Path.home()/'.m2/repository/net/funtimeevents/poc'/f'mc{mc}'/VERSION/f'mc{mc}-{VERSION}.jar'
    digest=hashlib.sha256(sdk.read_bytes()).hexdigest()
    for attempt in valid:
        path=ROOT/attempt['path'];observed=json.loads(path.read_text(encoding='utf-8'));runtime=observed['runtime']
        log=(path.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
        server=(path.parent/'server.txt').read_text(encoding='utf-8',errors='replace')
        checks={}
        def check(name,value):checks[name]=bool(value)
        check('production_maven_nested',runtime['development'] is False and runtime['origin']=='NESTED' and runtime['sdk_version']==VERSION and observed['sdk_sha256']==digest)
        check('java_namespace',runtime['java']==java and runtime['namespace']==namespace)
        check('normal_exits',observed.get('client_exit')==0 and observed.get('server_exit')==0 and 'BUILD SUCCESSFUL' in log and 'BUILD SUCCESSFUL' in server)
        check('all_real_sessions',runtime['joins']==runtime['disconnects']==len(fixtures)==len(runtime['sessions']) and len(re.findall(r'\[PHASE3-SERVER\] JOIN session=',server))==len(fixtures))
        check('queue_drained',runtime['handoff_empty'] is True)
        check('all_states_reset_on_render',all(s['state_cleared'] is True and s['disconnect_handled']=='Render thread' for s in runtime['sessions']))
        check('one_start_stop_per_tracker_session',all(log.count(f'[FTE:TRACK] {name} started')==len(fixtures) and log.count(f'[FTE:TRACK] {name} stopped')==len(fixtures)
            for name in ['DungeonTracker','HellMapTracker','TabTracker','MineTracker','EventCoordinatesTracker']))
        check('default_200_tick_schedule',runtime['periodic']==sum(s['ticks']//200 for s in runtime['sessions'])==9)
        check('periodic_payload_ticks',all(p['tick']%200==0 for p in runtime['payloads'] if p['type'] in ['tab','copper','warden','mine']))
        check('hell_payload_ticks',all(p['tick'] in [100,200] for p in runtime['payloads'] if p['type']=='hell'))
        expected_callbacks=[(i,m) for i,f in enumerate(fixtures,1) for m in f['messages']]
        check('actual_fabric_message_delivery',len(runtime['callbacks'])==len(expected_callbacks) and all(
            row['session']==i and row['id']==m['id'] and row['channel']==m['channel'] and m['text'] in row['plain'] and row['hover']==m.get('hover','')
            for row,(i,m) in zip(runtime['callbacks'],expected_callbacks)))
        sent=re.findall(r'\[PHASE3-SERVER\] MESSAGE session=(\d+) id=(\S+)',server)
        check('server_sent_exact_messages',sent==[(str(i),m['id']) for i,m in expected_callbacks])
        matrix={}
        for i,f in enumerate(fixtures,1):
            actual={t:[] for t in f['expected']}
            for p in runtime['payloads']:
                if p['session']==i:actual[p['type']].append(p['payload'])
            check('golden_'+f['name'],canonical(actual)==canonical(f['expected']))
            for t,values in f['expected'].items():
                if values:matrix[t]=matrix.get(t,0)+len(values)
        snapshots=[json.loads(m) for m in re.findall(r'\[PHASE3\] WORLD_SNAPSHOT (.*)',log)]
        check('real_dimensions_observed',len(snapshots)==len(fixtures) and all(s['dimension']==f['dimension'] for s,f in zip(snapshots,fixtures)))
        check('actual_three_container_types',all(snapshots[i]['blocks'][:3]==['minecraft:chest','minecraft:barrel','minecraft:trapped_chest'] for i in [0,2]))
        check('npc_world_positions_and_metadata',all(all(any(p['name']==n['name'] and [p['x'],p['y'],p['z']]==n['pos'] and p['invisible']==n['invisible'] for p in snapshot['world_players'])
            for n in f['npcs'] if n['entity']) for snapshot,f in zip(snapshots,fixtures)))
        check('tab_only_npc_not_world_entity',all(all(n['name'] not in {p['name'] for p in s['world_players']} for n in f['npcs'] if not n['entity']) for s,f in zip(snapshots,fixtures)))
        check('no_dungeon_world_scan_outside_zone',all(s['entity_visits']==s['text_display_reads']==s['block_reads']==0 for s in runtime['sessions'] if s['name'] not in ['copper','warden']))
        check('actual_entities_and_text_displays_scanned',all(s['entity_visits']>=200 and s['text_display_reads']>=20 and s['block_reads']>=9 for s in runtime['sessions'] if s['name'] in ['copper','warden']))
        check('twelve_fast_reconnects',len([s for s in runtime['sessions'] if s['name'].startswith('rapid-') and s['ticks']<100])==12)
        check('boss_updates_and_removal_real_commands',all(f'COMMAND session=7 action={action}' in server for action in ['boss9','remove','next']))
        check('all_payload_types',set(p['type'] for p in runtime['payloads'])==set(matrix))
        metrics=runtime['performance'];check('metrics_cadence',all(metrics[t][0]==9 for t in ['dungeon','mine','tab']) and metrics['hell'][0]==17)
        check('timings_recorded',all(v[1]>=v[2]>0 for v in metrics.values()))
        profiling={}
        if runtime.get('callback_performance'):
            cp=runtime['callback_performance']
            profiling['measured_real_message_paths']=all(cp[t][0]==n and cp[t][1]>=cp[t][2]>0 and cp[t][3]>=cp[t][4]>=0
                for t,n in [('ban_message',15),('coordinates_message',20),('ordinary_message',5)])
            profiling['idle_sdk_callback_zero_java_allocations']=cp['sdk_tick_idle'][0]>=100 and cp['sdk_tick_idle'][3]==cp['sdk_tick_idle'][4]==0
        result['runs'].append({'path':attempt['path'],'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,
            'runtime_vendor':runtime.get('java_vendor','see raw client executable'),'runtime_version':runtime.get('java_runtime','see raw client executable'),
            'fixture_revision':runtime.get('fixture_revision',1),
            'profiling_checks':profiling,
            'payload_counts':matrix,'callbacks':len(runtime['callbacks']),'sessions':len(runtime['sessions']),
            'performance_ms':{t:{'scans':v[0],'mean':v[1]/v[0]/1e6,'max':v[2]/1e6} for t,v in metrics.items()}})
    with zipfile.ZipFile(sdk) as z:
        names=z.namelist();p=result['packaging']
        trackers=['dungeon/DungeonTracker','hell/HellMapTracker','tab/TabTracker','mine/MineTracker','eventcoordinates/EventCoordinatesTracker']
        p['one_of_each_tracker']=all(names.count('net/funtimeevents/tracker/'+t+'.class')==1 for t in trackers)
        p['two_common_mixins']=all('net/funtimeevents/poc/platform/mixin/'+n+'.class' in names for n in ['BossHealthOverlayAccessor','TextDisplayAccessor'])
        p['mixin_config_referenced']='fte-trackers.mixins.json' in json.loads(z.read('fabric.mod.json'))['mixins']
        p['one_of_each_version_helper']=all(names.count('net/funtimeevents/poc/platform/'+n+'.class')==1 for n in ['HoverAccess','VersionAccess'])
        p['no_fixtures_or_network']=not any(n.startswith(('phase2/','phase3/','net/funtimeevents/poc/fixture/','net/funtimeevents/net/')) for n in names)
        p['core_classes_java21']=all(struct.unpack_from('>H',z.read('net/funtimeevents/'+str(target.relative_to(spec.CORE)).replace('\\','/').replace('.java','.class')),6)[0]==65
            for _,target,_ in spec.expected_files() if target.is_relative_to(spec.CORE))
        p['namespace_for_new_code']=all(b'net/minecraft/client/Minecraft' not in z.read(n) for n in names if n.endswith('.class')) if mc=='1.21.4' else b'net/minecraft/client/Minecraft' in z.read('net/funtimeevents/tracker/dungeon/DungeonTracker.class')
    result['required_repeats']=args.min26 if mc=='26.3' else 1
    result['latest_fixture_observed']=any(r['fixture_revision']>=3 for r in result['runs'])
    result['status']='PASS' if len(valid)>=result['required_repeats'] and result['latest_fixture_observed'] and all(r['status']=='PASS' and all(r['profiling_checks'].values()) for r in result['runs']) and all(result['packaging'].values()) else 'FAIL'
    summary['targets'].append(result)
summary['status']='PASS' if all(source_checks.values()) and all(t['status']=='PASS' for t in summary['targets']) else 'FAIL'
summary['functional_status']=summary['status']
summary['native_failures']=[a for t in summary['targets'] for a in t['attempts'] if a.get('native_exit_codes')]
summary['native_startup_status']='OPEN_REPRODUCED' if summary['native_failures'] else 'NO_NATIVE_FAILURE_OBSERVED'
if summary['functional_status']=='PASS' and summary['native_failures']:summary['status']='FUNCTIONAL_PASS_NATIVE_OPEN'
out=ROOT/'results/phase3-summary.json';out.write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for target in summary['targets']:
    print(target['target'],target['status'],'successful production runs',target['successful_repeats'])
    for run in target['runs']:
        print(run['path'],run['status'],f"{sum(run['checks'].values())}/{len(run['checks'])}",run['payload_counts'])
        for name,value in run['checks'].items():
            if not value:print('FAIL',name)
print('Functional:',summary['functional_status'],'Native startup:',summary['native_startup_status'])
raise SystemExit(summary['functional_status']!='PASS')
