"""Old SDK public binary/source signatures and frozen Phase 4 algorithm guard."""
import argparse,hashlib,json,os,subprocess,sys,zipfile
from pathlib import Path
from run_phase2 import ROOT

def one(mc):
    prefix=Path(os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6'))/mc
    folder=ROOT/prefix/'api-details';folder.mkdir(parents=True,exist_ok=True)
    config=json.loads((ROOT/'targets.json').read_text())
    command=[sys.executable,'verify_phase4_compat.py','--version',config['sdk_version'],'--targets',mc,'--output',str(prefix/'api-details')]
    with (folder/'verification.txt').open('wb') as log:r=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    result={'status':'FAIL','exit':r.returncode,'command':command}
    if (folder/'result.json').exists():
        observed=json.loads((folder/'result.json').read_text());result['classes']=len(observed['scope']);result['old_jar_sha256']=observed['old_sha256']
        result['status']='PASS' if r.returncode==0 and len(observed['scope'])==36 else 'FAIL'
    (ROOT/prefix/'api.json').write_text(json.dumps(result,indent=2)+'\n')
    print(mc,'API',result['status'],flush=True);return result

def sources():
    from phase6_source_parity import verify
    return verify()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--targets',default='all');a=p.parse_args()
    targets=[t['minecraft'] for t in json.loads((ROOT/'targets.json').read_text())['targets']] if a.targets=='all' else a.targets.split(',')
    results=[sources()]+[one(mc) for mc in targets]
    raise SystemExit(any(r['status']!='PASS' for r in results))
