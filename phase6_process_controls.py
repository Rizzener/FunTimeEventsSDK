"""Verify fixture supervision kills its own descendants and preserves an unrelated control."""
import json,os,subprocess,sys,time
from pathlib import Path
from run_phase2 import ROOT,spawn_owned,terminate_owned

def alive(pid):
    if os.name!='nt':
        p=Path('/proc')/str(pid)/'stat'
        return p.exists() and p.read_text().split(') ',1)[1].split()[0]!='Z'
    import ctypes
    handle=ctypes.windll.kernel32.OpenProcess(0x1000,False,pid)
    if not handle:return False
    value=ctypes.c_ulong();ctypes.windll.kernel32.GetExitCodeProcess(handle,ctypes.byref(value));ctypes.windll.kernel32.CloseHandle(handle)
    return value.value==259

def main():
    out=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/'process-control';out.mkdir(parents=True,exist_ok=True)
    marker=out/'child-pid.txt';marker.unlink(missing_ok=True)
    unrelated=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
    script='import subprocess,sys,time;from pathlib import Path;child=subprocess.Popen([sys.executable,"-c","import time;time.sleep(60)"]);Path(sys.argv[1]).write_text(str(child.pid));time.sleep(60)'
    parent=spawn_owned([sys.executable,'-c',script,str(marker)])
    try:
        deadline=time.monotonic()+10
        while not marker.exists() and time.monotonic()<deadline:time.sleep(.05)
        assert marker.exists(),'Owned child did not start'
        child=int(marker.read_text());terminate_owned(parent)
        deadline=time.monotonic()+5
        while alive(child) and time.monotonic()<deadline:time.sleep(.05)
        checks={'owned_parent_terminated':not alive(parent.pid),'owned_child_terminated':not alive(child),'unrelated_control_alive':unrelated.poll() is None}
        value={'status':'PASS' if all(checks.values()) else 'FAIL','os':sys.platform,'checks':checks}
        (out/'result.json').write_text(json.dumps(value,indent=2)+'\n');print('OWNED_PROCESS_CONTROL',value)
        return all(checks.values())
    finally:
        terminate_owned(parent);unrelated.terminate();unrelated.wait(timeout=10)
if __name__=='__main__':raise SystemExit(not main())
