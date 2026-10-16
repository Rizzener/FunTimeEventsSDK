"""Reclaim only regenerable game inputs in completed fixture run directories.

Raw logs, results, SDK artifacts and crash diagnostics are never removed.
Run only after the corresponding fixture processes have exited.
"""
import argparse,datetime,hashlib,json,shutil,os
from pathlib import Path
from run_phase2 import ROOT

def reclaim(mc,phase2=False):
    removed=[]
    backing=Path('/mnt/c') if Path('/mnt/c').is_dir() else ROOT
    retain_jars=shutil.disk_usage(backing).free>5*1024**3
    dirs=[ROOT/'targets'/('mc'+mc)/'build/phase3-server',ROOT/'consumers'/('mc'+mc)/'build/phase3-client']
    if phase2:
        dirs += [ROOT/'targets'/('mc'+mc)/'build/phase2-server',ROOT/'consumers'/('mc'+mc)/'build/phase2-client']
    for directory in dirs:
        directory=directory.resolve();assert directory.is_relative_to(ROOT.resolve()) and 'build' in directory.parts
        for path in directory.rglob('*.jar'):
            if retain_jars:continue
            if path.stat().st_size<500_000:continue
            removed.append({'path':str(path.relative_to(ROOT)),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
            path.unlink()
        if directory.parent.parent.parent.name=='targets':
            for world in directory.glob('phase3-world-*'):
                if not world.is_dir():continue
                assert world.resolve().parent==directory
                removed.append({'path':str(world.relative_to(ROOT)),'bytes':sum(p.stat().st_size for p in world.rglob('*') if p.is_file()),'type':'regenerable fixture world'})
                shutil.rmtree(world)
    out=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/'local-environment/reclaimed-inputs';out.mkdir(parents=True,exist_ok=True)
    path=out/(mc+'-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.json')
    path.write_text(json.dumps({'removed':removed,'retained_input_jars':retain_jars,'backing_free_after':shutil.disk_usage(backing).free,'free_after':shutil.disk_usage(ROOT).free},indent=2)+'\n')
    print(mc,'reclaimed MiB',sum(r['bytes'] for r in removed)//1048576,'free MiB',shutil.disk_usage(ROOT).free//1048576,flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);p.add_argument('--historical',action='store_true');a=p.parse_args()
    for mc in a.targets.split(','):reclaim(mc,a.historical)
