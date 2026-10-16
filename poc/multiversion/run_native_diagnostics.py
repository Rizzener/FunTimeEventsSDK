"""Separate startup control: the existing smoke branch never starts trackers.

Optional Microsoft ProcDump attaches ONLY to the tagged diagnostic client PID.
Does not change JDK, driver, registry crash policy, GC or JIT settings.
"""
from pathlib import Path
import argparse
import datetime
import json
import os
import subprocess
import time
from run_phase2 import ROOT, gradle, terminate_owned
from run_phase3 import diagnostics
from native_dump import retain_wer_dump


def diagnostic_pid(baseline=False):
    tag='baseline' if baseline else 'diagnostic'
    script = "Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*-Dfte.native."+tag+"=true*' } | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"
    p=subprocess.run(['powershell','-NoProfile','-Command',script],capture_output=True,text=True,timeout=10)
    if p.returncode!=0:raise RuntimeError(p.stderr)
    if not p.stdout.strip():return None
    value=json.loads(p.stdout)
    if isinstance(value,list):raise RuntimeError('Multiple tagged clients; refusing ambiguous PID')
    return value


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--attempts',type=int,default=3)
    parser.add_argument('--procdump',type=Path)
    parser.add_argument('--baseline',action='store_true',help='Exclude SDK AND consumer; observe a 45 second startup window, then stop only that process tree')
    args=parser.parse_args()
    root=ROOT/'results/phase3/native-investigation';root.mkdir(exist_ok=True)
    rows=[]
    for index in range(args.attempts):
        stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
        out=root/(('no-sdk-' if args.baseline else 'startup-')+stamp);out.mkdir()
        started=time.time();monitor=None;monitor_log=None;pid_data=None
        command=gradle('-p','consumers/mc26.3','prodClient','-PnativeBaseline' if args.baseline else '-PnativeDiagnostic')
        survived=False;dump_records=[]
        with (out/'client.txt').open('wb') as log:
            client=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
            try:
                while client.poll() is None:
                    if time.time()-started>180:raise TimeoutError('Native startup diagnostic timeout')
                    if args.baseline:
                        if pid_data is None:pid_data=diagnostic_pid(True)
                        if pid_data:dump_records.extend(retain_wer_dump(pid_data['ProcessId'],started,ROOT/'.evidence/native-dumps'/out.name))
                    if args.baseline and time.time()-started>=45:
                        survived=True
                        if pid_data is None:pid_data=diagnostic_pid(True)
                        if pid_data:
                            p=subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/jcmd.exe'),str(pid_data['ProcessId']),'Thread.print'],
                                capture_output=True,encoding='utf-8',errors='replace',timeout=15)
                            (out/'threads.txt').write_text(p.stdout+p.stderr,encoding='utf-8')
                        break
                    if args.procdump and monitor is None:
                        pid_data=diagnostic_pid(args.baseline)
                        if pid_data:
                            dumps=ROOT/'.evidence/native-dumps'/out.name;dumps.mkdir(parents=True,exist_ok=True)
                            monitor_log=(out/'procdump.txt').open('wb')
                            monitor=subprocess.Popen([str(args.procdump.resolve()),'-accepteula','-e','-mm',
                                str(pid_data['ProcessId']),str(dumps)],stdout=monitor_log,stderr=subprocess.STDOUT)
                    time.sleep(0.5)
                exit_code=client.returncode
            finally:
                terminate_owned(client)
                if monitor:
                    try:monitor.wait(timeout=10)
                    except subprocess.TimeoutExpired:terminate_owned(monitor)
                if monitor_log:monitor_log.close()
                if args.baseline and pid_data:
                    for _ in range(10):
                        dump_records.extend(retain_wer_dump(pid_data['ProcessId'],started,ROOT/'.evidence/native-dumps'/out.name))
                        time.sleep(0.5)
        diagnostics(ROOT/'consumers/mc26.3/build'/('native-baseline' if args.baseline else 'native-diagnostic'),out,started)
        text=(out/'client.txt').read_text(encoding='utf-8',errors='replace')
        row={'command':command,'exit':exit_code,'status':('SURVIVED_WINDOW_CONTROLLED_STOP' if survived else 'NATIVE_OR_STARTUP_FAILURE') if args.baseline else ('PASS' if exit_code==0 and '[FTE-POC] RUNTIME_PASS' in text else 'FAIL'),
             'tracked_pid':pid_data,'trackers_started': '[FTE:TRACK]' in text,'seconds':round(time.time()-started,2)}
        row['sdk_listed']='fte-multiversion-poc' in text;row['consumer_listed']='poc-consumer-263' in text
        row['dump_capture']=dump_records
        if args.baseline and survived and (not pid_data or 'Created window using SDL' not in text):row['status']='INCOMPLETE_STARTUP_CONTROLLED_STOP'
        (out/'result.json').write_text(json.dumps(row,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        rows.append(row);print(out.name,row['status'],'trackers_started',row['trackers_started'],flush=True)
    # Failures remain evidence; do not classify an intermittent crash as fixed.
    raise SystemExit(any(row['status'] not in ['PASS','SURVIVED_WINDOW_CONTROLLED_STOP'] for row in rows))


if __name__=='__main__':main()
