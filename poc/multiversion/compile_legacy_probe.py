import argparse,hashlib, json, shutil, subprocess
from pathlib import Path
from run_phase2 import ROOT

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',default='results/phase4/compat');args=parser.parse_args()
    old=ROOT/'fixtures/binary-compat/legacy-sdk.jar'
    if not old.exists():old=ROOT.parents[1]/'fte-api/build/libs/fte-api-1.0.5-SNAPSHOT.jar'
    dest=ROOT/'fixtures/binary-compat/classes';dest.mkdir(exist_ok=True)
    command=(['rtk','proxy'] if shutil.which('rtk') else [])+['javac','--release','21','-cp',str(old),'-d',str(dest),str(ROOT/'fixtures/binary-compat/LegacyApiProbe.java')]
    p=subprocess.run(command,capture_output=True)
    out=ROOT/args.output;out.mkdir(parents=True,exist_ok=True)
    (out/'legacy-compile.txt').write_bytes(p.stdout+p.stderr)
    result={'command':command,'exit':p.returncode,'old_jar_sha256':hashlib.sha256(old.read_bytes()).hexdigest()}
    if p.returncode==0:result['probe_sha256']=hashlib.sha256((dest/'net/funtimeevents/poc/compat/LegacyApiProbe.class').read_bytes()).hexdigest()
    (out/'legacy-compile.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(result);raise SystemExit(p.returncode)
