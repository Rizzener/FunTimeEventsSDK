"""Sequential local Linux CI stages. Never overwrites a published SDK version."""
import argparse,json,os,shutil,subprocess,sys,traceback
from pathlib import Path
from run_phase2 import ROOT
from phase5_build import run as build
from phase5_run import network,runtime,CONFIG,PREFIX
from phase5_api import one as api,sources
import phase5_verify as verify
from phase5_space import reclaim

def passed(mc,kind):return bool(verify.successful(mc,kind))
def main():
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);p.add_argument('--run-label',default='matrix');a=p.parse_args()
    assert all(c.isalnum() or c=='-' for c in a.run_label)
    targets=[t['minecraft'] for t in CONFIG['targets']] if a.targets=='all' else a.targets.split(',')
    rows={};sources()
    probe=subprocess.run([sys.executable,'compile_legacy_probe.py','--output',str(PREFIX/'legacy-binary')],cwd=ROOT)
    if probe.returncode:raise SystemExit(probe.returncode)
    for mc in targets:
        row={};rows[mc]=row
        try:
            for stage in ['compile','publish','consumer']:
                result={'status':'PASS','previous_evidence':True} if passed(mc,stage) else build(mc,stage,CONFIG['sdk_version'])
                row[stage]=result['status']
                if result['status']!='PASS':raise RuntimeError(stage+' failed; dependent checks NOT VERIFIED')
            row['api']=api(mc)['status'];row['artifacts']=verify.artifacts(mc)['status']
            if not passed(mc,'network'):network(mc)
            row['network']=verify.network(mc)['status']
            checked=verify.runtime(mc)
            for mode in ['offline','rest','ws','flags']:
                if checked['modes'][mode]['status']!='PASS':runtime(mc,mode)
            row['runtime']=verify.runtime(mc)['status']
            if mc=='26.3':
                # Two additional fresh production JVM/server starts; OPEN stays OPEN.
                row['startup_repeats']=[runtime(mc,'rest')['status'] for _ in range(2)]
        except BaseException as error:
            row['error']=repr(error);traceback.print_exc()
        finally:
            # All supervised processes have exited before game inputs are reclaimed.
            reclaim(mc)
            complete=len(rows)==len(targets) and all(all(v=='PASS' for k,v in r.items() if k!='startup_repeats') and all(s=='PASS' for s in r.get('startup_repeats',[])) for r in rows.values())
            path=ROOT/PREFIX/('linux-'+a.run_label+'.json');path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(json.dumps({'requested_targets':targets,'rows':rows,'native_status':'OPEN','status':'PASS' if complete else 'NOT VERIFIED'},indent=2)+'\n')
            print('LINUX TARGET',mc,row,flush=True)
            if os.environ.get('FTE_PHASE5_COLLECT'):
                shutil.copytree(ROOT/PREFIX,Path(os.environ['FTE_PHASE5_COLLECT']),dirs_exist_ok=True)
    verify.summary();raise SystemExit(not complete)

if __name__=='__main__':main()
