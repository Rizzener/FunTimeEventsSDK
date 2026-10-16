"""Run each approved stage in production; every attempt and crash is retained."""
import argparse, datetime, json, subprocess, sys
from run_phase2 import ROOT, wait_port, terminate_owned
from run_phase3 import run_target

def one(mc,stage,version,mode,external,flags=False):
    backend=None;log=None;output=None
    try:
        address=external
        if stage!='a' and not external:
            parent=ROOT/'results/phase4'/('4'+stage.upper())/mc
            output=parent/('backend-'+mode+'-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
            output.mkdir(parents=True)
            log=(output/'server.txt').open('wb')
            command=['rtk','proxy',sys.executable,'-X','utf8',str(ROOT/'fixtures/fake_backend.py'),'--output',str(output/'raw.jsonl'),
                     '--tls-dir',str(ROOT/'.evidence/phase4-tls')]
            backend=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
            wait_port(29841,backend,30);address='https://127.0.0.1:29841'
        result=run_target(mc,phase4=stage,version=version,backend=address,mode=mode,flags=flags)
        result['mode']=mode
        result['individual_flags']=flags
        if output:result['backend_evidence']=str(output.relative_to(ROOT))
        target=ROOT/'results/phase4'/('4'+stage.upper())/mc/('attempt-%02d'%result['attempt'])/'result.json'
        target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        return result
    finally:
        if backend:terminate_owned(backend)
        if log:log.close()

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--stage',choices=['a','b','c'],required=True)
    p.add_argument('--target',choices=['all','1.21.4','26.3'],default='all')
    p.add_argument('--repeats',type=int,default=1)
    p.add_argument('--backend')
    p.add_argument('--mode',choices=['both','ws','rest','plain'],default='both')
    p.add_argument('--flags',action='store_true',help='Real per-tracker disable checks across fresh public SDK runtimes')
    p.add_argument('--version')
    a=p.parse_args()
    version=a.version or {'a':'0.0.7-phase4a.2','b':'0.0.8-phase4b','c':'0.0.9-phase4c'}[a.stage]
    targets=['1.21.4','26.3'] if a.target=='all' else [a.target]
    modes=['offline'] if a.stage=='a' else ['rest','ws'] if a.mode=='both' else [a.mode]
    results=[one(mc,a.stage,version,mode,a.backend,a.flags) for mc in targets for mode in modes for _ in range(a.repeats)]
    raise SystemExit(any(r['status']!='PASS' for r in results))
