"""Retain every standalone Java verification attempt and local backend trace."""
import argparse, hashlib, json, subprocess, sys, zipfile
from pathlib import Path
from run_phase2 import ROOT, gradle, wait_port, terminate_owned

def run(stage,kind,frozen=False):
    version={'a':'0.0.7-phase4a.2','b':'0.0.8-phase4b','c':'0.0.9-phase4c'}[stage]
    # Historical stage labels must never compile today's transport as yesterday's baseline.
    published=Path.home()/'.m2/repository/net/funtimeevents/poc/mc26.3'/version/('mc26.3-'+version+'-sources.jar')
    if frozen:
        if stage!='b' or kind not in ['parity','faults','late']:raise SystemExit('--frozen is supported for 4B parity/faults/late')
        manifest=json.loads((ROOT/'results/phase4/evidence-manifest.json').read_text(encoding='utf-8'))['artifacts']
        for mc in ['1.21.4','26.3']:
            relative=Path('results/phase4/artifacts')/version/('mc'+mc)/('mc'+mc+'-'+version+'.jar')
            expected=manifest.get(str(relative)) or manifest.get(relative.as_posix())
            if expected is None or hashlib.sha256((ROOT/relative).read_bytes()).hexdigest()!=expected['sha256']:
                raise SystemExit('Missing/changed frozen baseline '+str(relative))
    elif published.exists():
        with zipfile.ZipFile(published) as source:
            name='net/funtimeevents/net/ApiClient.java'
            current=(ROOT/'platform/common/src/main/java'/name).read_text(encoding='utf-8')
            if source.read(name).decode('utf-8').replace('\r\n','\n')!=current:
                raise SystemExit('Current sources differ from frozen '+version+'; use its retained source archive in a separate checkout. Stage 4C runs against current candidate sources.')
    parent=ROOT/'results/phase4'/('4'+stage.upper())/kind;parent.mkdir(parents=True,exist_ok=True)
    output=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));output.mkdir()
    backend=None;server_log=None
    try:
        if kind!='config':
            server_log=(output/'backend.txt').open('wb')
            port=29940 if kind in ['faults','late'] else 30040
            backend=subprocess.Popen(['rtk','proxy',sys.executable,'-X','utf8',str(ROOT/'fixtures/fake_backend.py'),
                '--port',str(port),'--output',str(output/'raw.jsonl'),'--tls-dir',str(ROOT/'.evidence/phase4-tls')],cwd=ROOT,stdout=server_log,stderr=subprocess.STDOUT)
            wait_port(port+1,backend,30)
        if kind=='config':tasks=[':mc1.21.4:verifyPublicConfig',':mc26.3:verifyPublicConfig']
        elif kind=='parity':tasks=[':mc1.21.4:verifyLegacyNetwork',':mc1.21.4:verifyNetworkParity',':mc26.3:verifyNetworkParity']
        elif kind=='faults':tasks=[':mc1.21.4:verifyLegacyFaults',':mc26.3:verifyNetworkFaults']
        elif kind=='late':tasks=[':mc26.3:verifyLateCallback']
        else:tasks=[':mc1.21.4:verifyNetworkHardening',':mc26.3:verifyNetworkHardening']
        command=gradle(*tasks,'-Ppoc_version='+version)
        if frozen:command.append('-Pnetwork_frozen_version='+version)
        if kind in ['faults','late']:command.append('-Pphase4_backend=https://127.0.0.1:29941')
        elif kind!='config':command.append('-Pphase4_backend=https://127.0.0.1:30041')
        with (output/'java.txt').open('wb') as log:
            p=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        result={'command':command,'frozen_transport':frozen,'exit':p.returncode,'status':'PASS' if p.returncode==0 else 'FAIL'}
        if kind=='faults':
            for mc in ['1.21.4','26.3']:
                probe=ROOT/'targets'/('mc'+mc)/'build/network-faults.json'
                if probe.exists():(output/(mc+'-observations.json')).write_bytes(probe.read_bytes())
        if kind=='late':
            probe=ROOT/'targets/mc26.3/build/late-callback.json'
            if probe.exists():(output/'observations.json').write_bytes(probe.read_bytes())
        if kind=='hardening':
            for mc in ['1.21.4','26.3']:
                probe=ROOT/'targets'/('mc'+mc)/'build/network-hardening.json'
                if probe.exists():(output/(mc+'-checks.json')).write_bytes(probe.read_bytes())
        (output/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(output,result['status'],flush=True);return result
    finally:
        if backend:terminate_owned(backend)
        if server_log:server_log.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--stage',choices=['a','b','c'],required=True)
    p.add_argument('--kind',choices=['config','parity','hardening','faults','late'],required=True)
    p.add_argument('--frozen',action='store_true',help='Use hash-verified retained 4B transport JARs before current classes on the test classpath')
    a=p.parse_args();raise SystemExit(run(a.stage,a.kind,a.frozen)['status']!='PASS')
