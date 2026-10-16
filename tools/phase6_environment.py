"""Record resource failures without changing host configuration."""
import ctypes,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class Memory(ctypes.Structure):
    _fields_=[('length',ctypes.c_ulong),('load',ctypes.c_ulong)]+[(n,ctypes.c_ulonglong) for n in ['total','available','pageTotal','pageAvailable','virtualTotal','virtualAvailable','extended']]
def main():
    out=ROOT/'results/phase6/local-environment/memory-failure';out.mkdir(parents=True,exist_ok=True)
    m=Memory();m.length=ctypes.sizeof(m);ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m))
    result={'memory_MiB':{n:getattr(m,n)//1048576 for n in ['total','available','pageTotal','pageAvailable']},'free_bytes':shutil.disk_usage(ROOT).free,'preserved':[]}
    for p in list(ROOT.glob('hs_err_pid*.log'))+list((Path.home()/'.gradle/workers').glob('*59212*')):
        shutil.copy2(p,out/p.name);result['preserved'].append(str(p))
    (out/'resources.json').write_text(json.dumps(result,indent=2)+'\n');print(result)
if __name__=='__main__':main()
