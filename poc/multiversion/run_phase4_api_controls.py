import json, subprocess, time
from run_phase2 import ROOT, gradle, terminate_owned
from run_phase3 import diagnostics

def run(mc):
    parent=ROOT/'results/phase4/4A'/mc/'api-controls';parent.mkdir(parents=True,exist_ok=True)
    output=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));output.mkdir()
    path=ROOT/'consumers'/('mc'+mc)/'build/production-run/phase4-api-control.json';path.unlink(missing_ok=True)
    command=gradle('-p','consumers/mc'+mc,'prodClient','-Pphase4stage=a','-Pphase4ApiOnly','-Ppoc_version=0.0.7-phase4a.2')
    started=time.time();result={'target':mc,'command':command,'status':'FAIL'}
    with (output/'client.txt').open('wb') as log:
        p=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        try:
            result['exit']=p.wait(timeout=240)
            raw=(output/'client.txt').read_text(encoding='utf-8',errors='replace')
            if p.returncode==0 and path.exists() and '[PHASE4A] PEER_MOD_PASS' in raw and '[PHASE4A] OLD_BINARY_PASS' in raw:
                result['runtime']=json.loads(path.read_text(encoding='utf-8'));result['status']=result['runtime']['status']
        except Exception as e:result['error']=repr(e)
        finally:terminate_owned(p)
    diagnostics(path.parent,output,started)
    (output/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(mc,result['status'],flush=True)
    return result
if __name__=='__main__':raise SystemExit(any(r['status']!='PASS' for r in [run('1.21.4'),run('26.3')]))
