"""Phase 5 production/network checks. Each attempt preserves raw evidence.

No remote hosts, SDK servers, or release coordinates are used.
"""
import argparse, datetime, hashlib, json, os, shutil, socket, subprocess, sys,time
from pathlib import Path
from run_phase2 import ROOT, gradle, wait_port, terminate_owned,spawn_owned
from run_phase3 import run_target

CONFIG=json.loads((ROOT/'targets.json').read_text())
PREFIX=Path(os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6'))

def attempt(mc,kind):
    parent=ROOT/PREFIX/mc/kind;parent.mkdir(parents=True,exist_ok=True)
    number=max([int(p.name.split('-')[-1]) for p in parent.glob('attempt-*')] or [0])+1
    out=parent/('attempt-%02d'%number);out.mkdir();return out

def backend_start(out,port):
    log=(out/'backend.txt').open('wb')
    command=[sys.executable,'-X','utf8',str(ROOT/'fixtures/fake_backend.py'),'--port',str(port),
             '--output',str(out/'raw.jsonl'),'--tls-dir',str(ROOT/'.evidence/phase4-tls')]
    process=spawn_owned(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    try:
        deadline=time.monotonic()+40
        while time.monotonic()<deadline:
            if process.poll() is not None:raise RuntimeError('Fixture server exited with '+str(process.returncode))
            if 'FAKE_BACKEND_READY' in (out/'backend.txt').read_text(encoding='utf-8',errors='replace'):break
            time.sleep(0.1)
        else:raise TimeoutError('Backend did not confirm both listeners ready')
    except Exception:
        terminate_owned(process);log.close();raise
    return process,log

def available_pair():
    # Windows Hyper-V/WSL reserves port ranges even when no socket is listening.
    # Probe both loopback listeners; never alter the OS reservations.
    for _ in range(100):
        with socket.socket() as first:
            if os.name=='nt':first.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
            first.bind(('127.0.0.1',0));port=first.getsockname()[1]
            if port==65535:continue
            try:
                with socket.socket() as second:
                    if os.name=='nt':second.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
                    second.bind(('127.0.0.1',port+1))
                return port
            except OSError:continue
    raise RuntimeError('No available pair of loopback ports')

def network(mc,kind='network'):
    out=attempt(mc,kind);process=None;log=None
    result={'target':mc,'status':'FAIL','started':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    try:
        index=next(i for i,t in enumerate(CONFIG['targets']) if t['minecraft']==mc)
        port=available_pair()
        process,log=backend_start(out,port)
        tasks=['verifySendFutureAccounting','verifyNetworkHardening'] if kind=='hardening' else ['verifyLegacyNetwork','verifyNetworkParity','verifyPublicConfig','verifySendFutureAccounting','verifyNetworkHardening']
        command=gradle(*[':mc'+mc+':'+t for t in tasks],'-Ptarget_versions='+mc,
            '-Pmod_version='+CONFIG['sdk_version'],'-Pphase4_backend=https://127.0.0.1:'+str(port+1))
        result['command']=command
        with (out/'java.txt').open('wb') as output:
            p=subprocess.run(command,cwd=ROOT,stdout=output,stderr=subprocess.STDOUT)
        result['exit']=p.returncode
        checks=ROOT/'targets'/('mc'+mc)/'build/network-hardening.json'
        if checks.exists():shutil.copy2(checks,out/'hardening.json')
        result['status']='PASS' if p.returncode==0 and checks.exists() else 'FAIL'
    except Exception as error:result['error']=repr(error)
    finally:
        if process:terminate_owned(process)
        if log:log.close()
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(mc,kind,result['status'],out.relative_to(ROOT),flush=True)
    return result

def runtime(mc,mode):
    index=next(i for i,t in enumerate(CONFIG['targets']) if t['minecraft']==mc)
    port=available_pair();process=None;log=None
    # Backend attempt is separate so failed startup itself remains visible.
    out=attempt(mc,'backend-'+mode)
    result={'target':mc,'mode':mode,'status':'FAIL'}
    try:
        address=None
        if mode!='offline':
            process,log=backend_start(out,port)
            address='https://127.0.0.1:'+str(port+1)
        evidence=PREFIX/mc/('runtime-'+mode)
        result=run_target(mc,phase4='a' if mode=='offline' else 'c',version=CONFIG['sdk_version'],
            backend=address,mode='rest' if mode=='flags' else mode,flags=mode=='flags',
            evidence=evidence,port=int(os.environ.get('FTE_PHASE5_GAME_PORT_BASE',33040 if os.name=='nt' else 34040))+index)
        result['mode']=mode;result['individual_flags']=mode=='flags'
        result['backend_evidence']=str(out.relative_to(ROOT))
        dest=ROOT/evidence/('attempt-%02d'%result['attempt'])
        (dest/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    except Exception as error:result['error']=repr(error)
    finally:
        if process:terminate_owned(process)
        if log:log.close()
    (out/'result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True)
    p.add_argument('--kind',choices=['network','hardening','runtime'],required=True)
    p.add_argument('--modes',default='offline,rest,ws,flags');p.add_argument('--repeats',type=int,default=1)
    a=p.parse_args();targets=[t['minecraft'] for t in CONFIG['targets']] if a.targets=='all' else a.targets.split(',')
    results=[]
    for mc in targets:
        for _ in range(a.repeats):
            if a.kind in ['network','hardening']:results.append(network(mc,a.kind))
            else:
                for mode in a.modes.split(','):
                    value=runtime(mc,mode);results.append(value)
                    if value['status']!='PASS':
                        print('FAIL_CLOSED remaining runtime modes NOT RUN',mc,mode,flush=True)
                        raise SystemExit(1)
        if a.kind=='runtime':
            from phase5_space import reclaim
            reclaim(mc)
    raise SystemExit(any(r['status']!='PASS' for r in results))
