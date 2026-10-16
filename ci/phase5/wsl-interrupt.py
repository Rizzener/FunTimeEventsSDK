"""Stop only the task-owned Linux CI group at a safe non-publication stage."""
import json,os,shutil,signal,time,sys
from pathlib import Path

workspace='native-b' if '--worker-b' in sys.argv else 'native'
task=Path('/var/tmp/fte-sdk-phase5-01a13db2')/workspace
root=task/'repo/poc/multiversion'
group=Path('/sys/fs/cgroup/fte-sdk-phase5-01a13db2'+('-b' if workspace=='native-b' else ''))
assert task.is_dir() and group.is_dir()
pids=[int(v) for v in (group/'cgroup.procs').read_text().split()]
commands={}
for pid in pids:
    try:commands[pid]=(Path('/proc')/str(pid)/'cmdline').read_bytes().replace(b'\0',b' ').decode(errors='replace')
    except FileNotFoundError:continue
matrix=[pid for pid,cmd in commands.items() if 'phase5_linux_matrix.py' in cmd.split()]
assert len(matrix)==1 and os.getpid() not in pids
os.kill(matrix[0],signal.SIGSTOP)
try:
    active=[]
    for pid in (group/'cgroup.procs').read_text().split():
        try:cmd=(Path('/proc')/pid/'cmdline').read_bytes().replace(b'\0',b' ').decode(errors='replace')
        except FileNotFoundError:continue
        if 'gradle-wrapper.jar' in cmd:active.append(cmd)
    assert not any('publish' in cmd for cmd in active),'Publication in progress: defer interruption'
    prefix=root/'results/phase5/wsl'/workspace
    assert not any(not (p/'result.json').exists() for p in prefix.glob('*/publish/attempt-*')),'Unrecorded publication: defer interruption'
    output=prefix/('runner-interruption-'+time.strftime('%Y%m%d-%H%M%S')+'.json')
    if (task/'xvfb.log').exists():shutil.copy2(task/'xvfb.log',output.with_suffix('.xvfb.txt'))
    output.write_text(json.dumps({'reason':'Restart supervisor to prove private Xvfb readiness and prevent WSLg fallback; SDK unchanged',
        'time':time.time(),'owned_group':str(group),'active_gradle_commands':active,'pids':pids},indent=2)+'\n')
    (group/'cgroup.kill').write_text('1')
except BaseException:
    os.kill(matrix[0],signal.SIGCONT);raise
time.sleep(2)
for p in (root/'results/phase5/wsl'/workspace).glob('*/**/attempt-*'):
    if not p.is_dir():continue
    if not (p/'result.json').exists():
        (p/'result.json').write_text(json.dumps({'status':'FAIL','error':'INTERRUPTED: supervisor restart for verified private Xvfb; SDK outcome not claimed'},indent=2)+'\n')
dest=Path('/mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/results/phase5/wsl')/workspace
shutil.copytree(root/'results/phase5/wsl'/workspace,dest,dirs_exist_ok=True)
print('Stopped only owned CI group; no Maven publication was active; raw evidence retained',flush=True)
