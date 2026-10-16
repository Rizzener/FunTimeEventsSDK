"""Bound only this task's local Linux jobs, without changing WSL global settings."""
import os,sys
from pathlib import Path
workspace=sys.argv[6] if len(sys.argv)>6 else 'native'
assert workspace in ['native','native-b']
group=Path('/sys/fs/cgroup/fte-sdk-phase5-01a13db2'+('-b' if workspace=='native-b' else ''))
group.mkdir(exist_ok=True)
(group/'memory.max').write_text(str(6*1024**3))
(group/'memory.swap.max').write_text(str(1024**3))
(group/'pids.max').write_text('2048')
cpus=int(os.environ.get('FTE_PHASE5_CPU_LIMIT','2'))
assert cpus in [2,4]
(group/'cpu.max').write_text(str(cpus*100000)+' 100000')
(group/'cgroup.procs').write_text(str(os.getpid()))
print('Task cgroup: workspace='+workspace+' memory=6GiB swap=1GiB cpu='+str(cpus)+' pids=2048',flush=True)
os.environ['FTE_PARENT_MOUNT_NS']=str(Path('/proc/self/ns/mnt').readlink())
isolate=str(Path(sys.argv[1]).with_name('wsl-isolate.sh'))
os.execv('/usr/bin/unshare',['unshare','--mount','--fork','/bin/sh',isolate,*sys.argv[1:]])
