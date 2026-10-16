"""Restore exact retained experimental artifacts, never rebuild historical stage code."""
import argparse, hashlib, json, shutil
from pathlib import Path
from run_phase2 import ROOT
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--version',required=True);a=p.parse_args()
    manifest=json.loads((ROOT/'results/phase4/evidence-manifest.json').read_text(encoding='utf-8'))['artifacts']
    count=0
    for relative,expected in manifest.items():
        source=ROOT/relative.replace('\\','/')
        if source.parent.parent.name!=a.version:continue
        if hashlib.sha256(source.read_bytes()).hexdigest()!=expected['sha256']:raise RuntimeError('Retained artifact changed: '+str(source))
        target=Path.home()/'.m2/repository/net/funtimeevents/poc'/source.parent.name/a.version/source.name
        target.parent.mkdir(parents=True,exist_ok=True)
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()!=expected['sha256']:raise RuntimeError('Refusing to replace different existing artifact: '+str(target))
        shutil.copy2(source,target);count+=1
    if count==0:raise SystemExit('No retained files for '+a.version)
    print('Restored',count,'verified Maven files for',a.version)
