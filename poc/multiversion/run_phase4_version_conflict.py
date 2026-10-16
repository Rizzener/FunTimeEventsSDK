"""Expected loader failure: two exact incompatible nested SDK versions."""
import json, subprocess
from run_phase2 import ROOT, gradle, terminate_owned
if __name__=='__main__':
    parent=ROOT/'results/phase4/4A/version-conflict';parent.mkdir(parents=True,exist_ok=True)
    output=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));output.mkdir()
    command=gradle('-p','consumers/mc1.21.4','prodClient','-Pphase4stage=a','-Pphase4ApiOnly',
                   '-Ppoc_version=0.0.8-phase4b','-Ppeer_sdk_version=0.0.7-phase4a.2','-Pphase4headlessFailure')
    timeout=False
    with (output/'client.txt').open('wb') as log:
        p=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        try:p.wait(timeout=120)
        except subprocess.TimeoutExpired:timeout=True
        finally:terminate_owned(p)
    raw=(output/'client.txt').read_text(encoding='utf-8',errors='replace')
    expected=not timeout and p.returncode!=0 and ('Incompatible mods found' in raw or 'Mod resolution encountered an incompatible mod set' in raw)
    expected=expected and '0.0.7-phase4a.2' in raw and '0.0.8-phase4b' in raw
    result={'status':'PASS' if expected else 'FAIL','expected':'loader rejection before entrypoints','actual_exit':p.returncode,'timeout':timeout,'command':command}
    (output/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(result['status'],output);raise SystemExit(not expected)
