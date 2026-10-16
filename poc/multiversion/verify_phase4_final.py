"""Aggregate retained evidence; failed attempts/native crashes remain explicit."""
import hashlib, json, re, subprocess
from run_phase2 import ROOT

def read(path):return json.loads((ROOT/path).read_text(encoding='utf-8'))
def latest_pass(parent):
    paths=sorted(p for p in (ROOT/parent).glob('attempt-*/result.json') if read(p.relative_to(ROOT))['status']=='PASS')
    if not paths:raise AssertionError('No successful evidence: '+parent)
    return paths[-1].parent

def main():
    checks={};performance=[];attempts=[]
    release_paths=['fte-api','build.gradle.kts','settings.gradle.kts','gradle.properties','gradle','jitpack.yml','README.md','DOC.md','AGENTS.md']
    guard=subprocess.run(['rtk','git','diff','--exit-code','--']+release_paths,cwd=ROOT.parents[1],capture_output=True,text=True,errors='replace')
    baseline=read('results/phase4/evidence-manifest.json')['legacy_baseline']
    legacy=ROOT.parents[1]/'fte-api/build/libs/fte-api-1.0.5-SNAPSHOT.jar'
    isolation={'tracked_release_diff_exit':guard.returncode,'output':guard.stdout+guard.stderr,
        'retained_legacy_sha256':hashlib.sha256((ROOT/baseline['path']).read_bytes()).hexdigest(),
        'expected_legacy_sha256':baseline['sha256'],'current_release_jar_sha256':hashlib.sha256(legacy.read_bytes()).hexdigest() if legacy.exists() else None}
    checks['release_isolation']=guard.returncode==0 and isolation['retained_legacy_sha256']==baseline['sha256'] and isolation['current_release_jar_sha256'] in [None,baseline['sha256']]
    (ROOT/'results/phase4/release-isolation.json').write_text(json.dumps(isolation,indent=2)+'\n',encoding='utf-8')
    for stage in ['4A','4B','4C']:
        summary=read('results/phase4/'+stage+'/summary.json')
        checks[stage+'_production']=summary['status']=='FUNCTIONAL_PASS_NATIVE_OPEN'
    for stage in ['4B','4C']:
        artifact=read('results/phase4/'+stage+'/artifact-checks.json')['checks']
        checks[stage+'_packaging_44']=len(artifact)==44 and all(r['status']=='PASS' for r in artifact)
        attempt=latest_pass('results/phase4/'+stage+'/parity')
        wire=read((attempt/'wire-comparison.json').relative_to(ROOT))
        checks[stage+'_wire_17']=len(wire['checks'])==17 and wire['status']=='PASS'
        if stage=='4B':
            log=(attempt/'java.txt').read_text(encoding='utf-8',errors='replace')
            checks['frozen_4B_reproduction']=read((attempt/'result.json').relative_to(ROOT)).get('frozen_transport',False) and all(
                '/0.0.8-phase4b/mc'+mc+'/mc'+mc+'-0.0.8-phase4b.jar' in log for mc in ['1.21.4','26.3'])
    compatibility=read('results/phase4/compat/0.0.9-phase4c/result.json')
    checks['public_36_classes']=len(compatibility['scope'])==36 and all(t['status']=='PASS' and t['source_compile_exit']==0 for t in compatibility['targets'].values())
    sources=read('results/phase4/4C/frozen-sources.json')
    checks['frozen_sources_104']=sum(map(len,sources.values()))==104 and all(all(v.values()) for v in sources.values())
    built=read((latest_pass('results/phase4/4C/consumer-build')/'result.json').relative_to(ROOT))
    checks['independent_consumer_builds']=len(built['commands'])==2 and all(c['exit']==0 for c in built['commands'])
    checks['current_config_both_JDKs']=read((latest_pass('results/phase4/4C/config')/'result.json').relative_to(ROOT))['status']=='PASS'
    hardening=latest_pass('results/phase4/4C/hardening')
    for mc in ['1.21.4','26.3']:
        data=read((hardening/(mc+'-checks.json')).relative_to(ROOT))
        checks[mc+'_hardening_10']=data['status']=='PASS' and len(data['checks'])==10 and all(data['checks'].values())
        control=latest_pass('results/phase4/4A/'+mc+'/api-controls')
        checks[mc+'_two_mods_old_binary']=read((control/'result.json').relative_to(ROOT))['status']=='PASS'
    conflict=latest_pass('results/phase4/4A/version-conflict')
    checks['different_nested_versions_rejected']=read((conflict/'result.json').relative_to(ROOT))['status']=='PASS'
    old_late=read('results/phase4/4B/late/attempt-01/observations.json')
    new_late=read((latest_pass('results/phase4/4C/late')/'observations.json').relative_to(ROOT))
    checks['real_late_callback_fixed']=not old_late['new_connection_delivered_after_late_old_rejection'] and new_late['new_connection_delivered_after_late_old_rejection']
    faults=read((latest_pass('results/phase4/4C/faults')/'26.3-observations.json').relative_to(ROOT))
    checks['fault_observations_fixed']=not faults['HTTP_serialization_on_caller'] and faults['unconnected_snapshot_serializations_5000_offers']==0 and faults['stop_explicitly_closed_InputStream'] and not faults['partial_cache_publication_observed'] and faults['noauth_unclosed_after_disconnect']==0
    for path in sorted((ROOT/'results/phase4').rglob('result.json')):
        if not path.parent.name.startswith('attempt-'):continue
        data=read(path.relative_to(ROOT));entry={'path':str(path.relative_to(ROOT)),'status':data.get('status')}
        if data.get('error'):entry['error']=data['error']
        log=path.parent/'client.txt'
        if log.exists():entry['native_codes']=sorted(set(re.findall('NTSTATUS (0xC000[0-9A-Fa-f]{4})',log.read_text(encoding='utf-8',errors='replace'))))
        attempts.append(entry)
        rt=data.get('runtime')
        if not rt or data['status']!='PASS' or data.get('individual_flags'):continue
        if rt.get('sdk_version') not in ['0.0.8-phase4b','0.0.9-phase4c']:continue
        samples=rt.get('callback_performance',{})
        performance.append({'path':str(path.relative_to(ROOT)),'target':data['target'],'mode':data['mode'],
            'version':rt['sdk_version'],'payloads':len(rt['payloads']),'periodic':rt['periodic'],
            'callbacks':samples,'shutdown_threads_empty':rt.get('shutdown_threads_empty'),
            'sessions':[{k:s[k] for k in ['session','name','entity_visits','text_display_reads','block_reads']} for s in rt['sessions']]})
    result={'status':'PASS_NATIVE_OPEN' if all(checks.values()) else 'FAIL','checks':checks,
        'native_status':'OPEN','failed_attempts':[a for a in attempts if a['status']!='PASS'],
        'all_attempts':attempts,'performance':performance,
        'scope':'Only Minecraft 1.21.4/Java21 and 26.3/Java25; local backend, no production-readiness claim'}
    (ROOT/'results/phase4/final-summary.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(result['status'],sum(checks.values()),'/',len(checks),[k for k,v in checks.items() if not v])
    print('Retained attempts',len(attempts),'failed',len(result['failed_attempts']))
    raise SystemExit(result['status']=='FAIL')

if __name__=='__main__':main()
