"""Run only missing production modes, preserving all previous attempts.

Use one invocation per disjoint target set. Never run the same target twice
concurrently: each consumer has one owned game directory and result file.
"""
import argparse,json,subprocess,os,time
from run_phase2 import ROOT,gradle
from phase5_run import CONFIG,PREFIX,attempt,runtime
import phase5_verify as verify

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--targets',default='all');parser.add_argument('--min-free-mib',type=int,default=0);args=parser.parse_args()
    targets=[t['minecraft'] for t in CONFIG['targets']] if args.targets=='all' else args.targets.split(',')
    failed=[]
    for mc in targets:
        checked=verify.runtime(mc)
        missing=[mode for mode,data in checked['modes'].items() if data['status']!='PASS']
        if missing:
            output=attempt(mc,'runtime-inputs')
            command=gradle('-p','consumers/mc'+mc,'downloadAssets','-Ppoc_version='+CONFIG['sdk_version'],
                           '-Pphase5_repository='+str(ROOT/'build/phase5-maven'))
            with (output/'gradle.txt').open('wb') as log:
                process=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
            (output/'result.json').write_text(json.dumps({'status':'PASS' if process.returncode==0 else 'FAIL','command':command,'exit':process.returncode},indent=2)+'\n')
            if process.returncode:
                failed.append(mc);continue
            for mode in missing:
                if os.name=='nt' and args.min_free_mib:
                    import ctypes
                    class Memory(ctypes.Structure):
                        _fields_=[('length',ctypes.c_ulong),('load',ctypes.c_ulong)]+[(n,ctypes.c_ulonglong) for n in ['total','available','page_total','page_available','virtual_total','virtual_available','extended']]
                    while True:
                        mem=Memory();mem.length=ctypes.sizeof(mem);ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(mem))
                        if mem.available//1048576>=args.min_free_mib:break
                        print('QUEUED',mc,mode,'free MiB',mem.available//1048576,'required',args.min_free_mib,flush=True);time.sleep(15)
                runtime(mc,mode)
        checked=verify.runtime(mc)
        if checked['status']!='PASS':failed.append(mc)
        print('RUNTIME TARGET',mc,checked['status'],flush=True)
        from phase5_space import reclaim
        reclaim(mc)
    verify.summary();raise SystemExit(bool(failed))

if __name__=='__main__':main()
