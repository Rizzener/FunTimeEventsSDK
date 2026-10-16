"""Analyze retained full-runtime CPU controls; do not infer a causal FPS result."""
import hashlib,json
from pathlib import Path
import phase5_verify as verify

def main():
    root=verify.ROOT;folder=root/'results/phase5/wsl/native/26.2/runtime-rest'
    runs=[(p,verify.load(p)) for p in sorted(folder.glob('attempt-*/result.json'))]
    baseline=next((p,d) for p,d in runs if d.get('status')=='PASS' and not d.get('performance_control') and d.get('display_receipt',{}).get('status')=='PASS')
    controls={cpus:[(p,d) for p,d in runs if d.get('status')=='PASS' and d.get('performance_control',{}).get('cpu_limit')==str(cpus)][-1] for cpus in [2,4]}
    verify.PREFIX=Path('results/phase5/wsl/native');checked=verify.runtime('26.2')
    valid={r['path'].replace('\\','/') for r in checked['modes']['rest']['runs'] if r['status']=='PASS'}
    rows=[]
    for label,(path,data) in [('primary_cpu2',baseline)]+[('diagnostic_cpu'+str(cpus),pair) for cpus,pair in controls.items()]:
        rt=data['runtime'];metric=rt['callback_performance']['sdk_tick_active']
        spans=sorted([s for s in rt.get('cpu_and_wall_spans',[]) if s['active']],key=lambda s:s['wall_ns'],reverse=True)
        rows.append({'case':label,'evidence':path.relative_to(root).as_posix(),'sdk_sha256':data['sdk_sha256'],
            'count':metric[0],'mean_us':metric[1]/metric[0]/1000,'max_us':metric[2]/1000,
            'long_active_spans':spans,'strict_pass':path.relative_to(root).as_posix() in valid})
    checks={'same_published_SDK_bytes':len({r['sdk_sha256'] for r in rows})==1,
            'all_three_full_runtime_pass':all(r['strict_pass'] for r in rows),
            'both_controls_measured_thread_CPU':all(r['long_active_spans'] for r in rows[1:])}
    artifacts={str(cpus):{p.name:hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (pair[0].parent/'artifacts').glob('*.jar')} for cpus,pair in controls.items()}
    checks['identical_consumer_peer_server_JARs_between_controls']=artifacts['2']==artifacts['4'] and len(artifacts['2'])==3
    counters={str(cpus):verify.load(root/('results/phase5/local-environment/performance-cpu-control/cpu'+str(cpus)+'-cgroup.json')) for cpus in [2,4]}
    stats={key:{name:int(value) for name,value in (line.split() for line in data['cpu.stat'].splitlines())} for key,data in counters.items()}
    delta={name:value-stats['2'].get(name,0) for name,value in stats['4'].items()}
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'rows':rows,'control_artifact_sha256':artifacts,
        'cgroup_cumulative_counters':counters,'cpu4_counter_delta_after_cpu2':delta,
        'scope':'Full SDK production REST with unchanged SDK JAR and golden assertions. Only consumer instrumentation and task CPU quota differ. Aggregate wall time includes scheduling/waits/VM pauses; Thread CPU nanosecond precision does not imply nanosecond accuracy. Single controls do not establish the cause of an earlier outlier or an FPS guarantee.'}
    path=root/'results/phase5/local-environment/performance-cpu-control/analysis.json'
    verify.save(path,result);print(result['status'],[(r['case'],r['mean_us'],r['max_us']) for r in rows])
    raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
