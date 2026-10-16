"""Recheck stable evidence only after both local WSL supervisors have exited."""
import argparse,ast,json,shutil,subprocess,sys,time
from run_phase2 import ROOT

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--workers-finished',action='store_true',required=True,
        help='Confirm both local matrix supervisors and collection have finished.')
    parser.parse_args()
    base=ROOT/'results/phase5/final-verification';base.mkdir(exist_ok=True)
    number=max([int(p.name.split('-')[-1]) for p in base.glob('attempt-*')]+[0])+1
    out=base/('attempt-%02d'%number);out.mkdir()
    commands=[
        ['phase5_verify.py','--targets','all','--stage','artifacts'],
        ['phase5_verify.py','--targets','all','--stage','network'],
        ['phase5_verify.py','--targets','all','--stage','runtime'],
        ['phase5_headless_check.py'],['phase5_collected_runtime.py'],
        ['phase5_linux_results.py'],['phase5_cross_os.py'],['phase5_integrity.py'],
        ['phase5_fixture_control.py'],['phase5_native_status.py'],
        ['-c',"import phase5_api; assert phase5_api.sources()['status']=='PASS'"],
        ['phase5_perf_control.py'],['phase5_perf.py'],['phase5_report.py']]
    records=[]
    for index,tail in enumerate(commands,1):
        command=[sys.executable,'-X','utf8',*tail]
        if shutil.which('rtk'):command=['rtk','proxy',*command]
        start=time.time();log=out/('%02d.txt'%index)
        with log.open('wb') as stream:
            process=subprocess.run(command,cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT)
        records.append({'command':command,'exit_code':process.returncode,
            'seconds':time.time()-start,'log':log.relative_to(ROOT).as_posix()})
        print(index,tail[0],process.returncode,flush=True)
    checks={}
    for name,expected in [('summary.json','FUNCTIONAL_PASS_NATIVE_OPEN'),
            ('linux-summary.json','PASS'),('cross-os.json','PASS'),('integrity.json','PASS'),('frozen-sources.json','PASS')]:
        data=json.loads((ROOT/'results/phase5'/name).read_text());checks[name]=data['status']==expected
    config=json.loads((ROOT/'targets.json').read_text())
    linux=json.loads((ROOT/'results/phase5/linux-summary.json').read_text())
    for mc in [target['minecraft'] for target in config['targets']]:
        for prefix in ['results/phase5',linux['targets'][mc]['prefix']]:
            data=json.loads((ROOT/prefix/mc/'api.json').read_text())
            checks['API_36_'+prefix+'_'+mc]=data['status']=='PASS' and data['classes']==36
    for worker,label,count in [('native','headless-a',7),('native-b','headless-b',6)]:
        data=json.loads((ROOT/'results/phase5/wsl'/worker/('linux-'+label+'.json')).read_text())
        checks['batch_'+label]=data['status']=='PASS' and len(data['rows'])==count
    native=json.loads((ROOT/'results/phase5/native-startup.json').read_text())
    checks['native_OPEN_with_six_verified_Linux_starts']=native['native_status']=='OPEN' and native['linux_six_verified_fresh_production_runs']
    checks['performance_controls_complete']=json.loads((ROOT/'results/phase5/local-environment/performance-cpu-control/analysis.json').read_text())['status']=='PASS'
    for path in list(ROOT.glob('*.py'))+list((ROOT/'ci/phase5').glob('*.py')):
        ast.parse(path.read_text(encoding='utf-8'))
    checks['current_Python_sources_parse']=True
    checks['report_FUNCTIONAL_PASS_NATIVE_OPEN']='FUNCTIONAL_PASS_NATIVE_OPEN**' in (ROOT/'PHASE5_RESULTS.md').read_text(encoding='utf-8')
    result={'status':'PASS' if all(record['exit_code']==0 for record in records) and all(checks.values()) else 'FAIL',
        'commands':records,'checks':checks,'operator_confirmed_workers_finished':True,
        'manifest_must_be_created_after_this_result':True}
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(result['status'],[key for key,value in checks.items() if not value],flush=True)
    raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
