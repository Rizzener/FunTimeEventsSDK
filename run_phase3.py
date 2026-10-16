"""Supervise production fixtures; retain every attempt, including crashes/timeouts.

Build/publish first. Uses the Phase 2 process helpers, never the release build.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import time
import zipfile
import sys
from run_phase2 import ROOT, gradle, wait_port, terminate_owned,maven_local


def diagnostics(run_dir, output, started):
    # Gradle may buffer a production child's output while it is still running.
    # Retain the actual game log/result even when the supervising task times out.
    for path in (run_dir/'logs').glob('*'):
        if path.is_file() and path.stat().st_mtime>=started-5:
            target=output/'game-logs'/path.name;target.parent.mkdir(exist_ok=True);shutil.copy2(path,target)
    observed=run_dir/'phase3-result.json'
    if observed.exists() and observed.stat().st_mtime>=started-5:shutil.copy2(observed,output/'client-result-observed.json')
    for pattern in ["hs_err_pid*.log", "replay_pid*.log", "crash-reports/*.txt"]:
        for path in run_dir.glob(pattern):
            if path.stat().st_mtime >= started - 5:
                target = output / "crash-diagnostics" / path.name
                target.parent.mkdir(exist_ok=True)
                shutil.copy2(path, target)
    if os.name == "nt":
        # Only Java crash events in this attempt's time window, not unrelated apps.
        stamp = datetime.datetime.fromtimestamp(started).isoformat(timespec="seconds")
        script = ("[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new(); $ErrorActionPreference='Stop'; try { Get-WinEvent -FilterHashtable "
                  "@{LogName='Application';StartTime=[datetime]'" + stamp + "';Id=1000,1001} "
                  "| Where-Object { $_.Message -match 'javaw?\\.exe' } "
                  "| Select-Object TimeCreated,Id,ProviderName,Message | ConvertTo-Json -Depth 4 } "
                  "catch { Write-Output $_.Exception.Message }")
        p = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True,
                           encoding="utf-8", errors="replace", timeout=30)
        (output / "windows-java-events.txt").write_text(p.stdout + p.stderr, encoding="utf-8")


def run_target(mc, vendor=None, phase4=None, version=None, backend=None, mode=None, flags=False, evidence=None, port=None):
    port = port or (28414 if mc == "1.21.4" else 28633)
    import socket
    with socket.socket() as probe:
        if os.name!='nt':probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        probe.bind(("127.0.0.1", port))
    root = ROOT / evidence if evidence else (ROOT / "results/phase4" / ('4'+phase4.upper()) / mc) if phase4 else ROOT / "results/phase3" / mc
    root.mkdir(parents=True, exist_ok=True)
    number = max([int(p.name.split('-')[-1]) for p in root.glob('attempt-*') if p.is_dir()] or [0]) + 1
    output = root / f"attempt-{number:02d}"
    output.mkdir()
    if phase4:
        with zipfile.ZipFile(output/'fixture-source.zip','w',zipfile.ZIP_DEFLATED) as archive:
            for prefix in ['consumers/common','fixtures/server','fixtures/data']:
                for path in (ROOT/prefix).rglob('*'):
                    if path.is_file():archive.write(path,path.relative_to(ROOT))
            archive.write(ROOT/'consumers/common/consumer.gradle','consumer-build.gradle.txt')
            for name in ['gradle/target.gradle','targets.json','build.gradle.kts','settings.gradle.kts',
                         'run_phase2.py','run_phase3.py','phase5_run.py','phase5_build.py','phase5_verify.py']:
                archive.write(ROOT/name,name)
    run_dir = ROOT / "consumers" / f"mc{mc}" / "build/phase3-client"
    client_json = run_dir / "phase3-result.json"
    client_json.unlink(missing_ok=True)
    stop_file = ROOT / "targets" / f"mc{mc}" / "build/phase3-server/phase3.stop"
    result = {"target": mc, "attempt": number, "status": "FAIL",
              "runner_os": sys.platform,
              "started": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "server_command": gradle(f":mc{mc}:phase2Server", "-Pphase3"),
              "client_command": gradle("-p", f"consumers/mc{mc}", "prodClient", "-Pphase3")}
    receipt=os.environ.get('FTE_PHASE5_DISPLAY_RECEIPT')
    if receipt and Path(receipt).is_file():
        result['display_receipt']=json.loads(Path(receipt).read_text(encoding='utf-8'))
        shutil.copy2(receipt,output/'display-receipt.json')
    if evidence:
        result['server_command'] += ['-Ptarget_versions='+mc, '-Pphase5_port='+str(port)]
        result['client_command'] += ['-Pphase5_port='+str(port),'-Pstaging_repository='+str(ROOT/'build/staging-maven')]
        if os.environ.get('FTE_PHASE5_PRELOAD')=='0':result['server_command'].append('-Pphase5_preload=false')
    if vendor:
        result['client_vendor_request']=vendor
        result['client_command'].append('-PdiagnosticVendor='+vendor)
    if phase4:
        result['client_command'].append('-Pphase4stage='+phase4)
        result['server_command'].append('-Pphase4stage='+phase4)
    if os.environ.get('FTE_PHASE5_THREAD_CPU')=='1':
        result['client_command'].append('-Pphase5cpu')
        result['performance_control']={'cpu_limit':os.environ.get('FTE_PHASE5_CPU_LIMIT'),'thread_cpu':True}
    if version:
        result['client_command'].append('-Pmod_version='+version)
        result['server_command'].append('-Pmod_version='+version)
    if backend:
        result['client_command'].append('-Pphase4backend='+backend)
    if mode:
        result['client_command'].append('-Pphase4mode='+mode)
    if flags:
        result['client_command'].append('-Pphase4flags')
    client = None
    started = time.time()
    result['fixture_sources_sha256']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [
        ROOT/'fixtures/server/common/src/main/java/net/funtimeevents/poc/fixture/Phase3Server.java',
        ROOT/'fixtures/data/phase3/tracker-sessions.json']}
    with (output / "server.txt").open('wb') as sl, (output / "client.txt").open('wb') as cl:
        server = subprocess.Popen(result["server_command"], cwd=ROOT, stdout=sl, stderr=subprocess.STDOUT)
        try:
            wait_port(port, server, 240)
            print(mc, "attempt", number, "server ready", flush=True)
            client = subprocess.Popen(result["client_command"], cwd=ROOT, stdout=cl, stderr=subprocess.STDOUT)
            result["client_exit"] = client.wait(timeout=600)
            if result["client_exit"] != 0 or not client_json.exists():
                raise RuntimeError(f"client exit={result['client_exit']}, fresh result={client_json.exists()}")
            result["runtime"] = json.loads(client_json.read_text(encoding='utf-8'))
            if result["runtime"]["status"] != "PASS": raise RuntimeError(result["runtime"].get("error"))
            version = result["runtime"]["sdk_version"]
            artifact = maven_local()/'com/github/Rizzener/FunTimeEventsSDK'/f'mc{mc}'/version/f'mc{mc}-{version}.jar'
            result["sdk_sha256"] = hashlib.sha256(artifact.read_bytes()).hexdigest()
            result["status"] = "PASS"
        except Exception as error:
            result["error"] = repr(error)
        finally:
            if client is not None: terminate_owned(client)
            stop_file.parent.mkdir(parents=True, exist_ok=True)
            stop_file.touch()
            try: result["server_exit"] = server.wait(timeout=60)
            except subprocess.TimeoutExpired:
                result["server_shutdown"] = "timeout; terminated only owned process tree"
                terminate_owned(server)
            if result.get("server_exit", -1) != 0: result["status"] = "FAIL"
    result["seconds"] = round(time.time()-started, 2)
    if phase4 and version:
        result['artifacts']=[]
        for pattern in [f'consumers/mc{mc}/build/libs/*{version}*.jar',f'targets/mc{mc}/build/libs/*{version}*server-fixture.jar']:
            for artifact in ROOT.glob(pattern):
                target=output/'artifacts'/artifact.name;target.parent.mkdir(exist_ok=True)
                shutil.copy2(artifact,target)
                result['artifacts'].append({'path':str(target.relative_to(ROOT)),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
    try: diagnostics(run_dir, output, started)
    except Exception as error: result["diagnostics_error"] = repr(error)
    (output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(mc, "attempt", number, result["status"], result.get("error", ''), flush=True)
    return result


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--target', choices=['all','1.21.4','26.3'],default='all')
    p.add_argument('--repeats26',type=int,default=3)
    p.add_argument('--vendor',choices=['AZUL'],help='Explicit diagnostic runtime comparison; default toolchain is unchanged')
    args=p.parse_args()
    targets=(['1.21.4'] if args.target in ['all','1.21.4'] else []) + (['26.3']*args.repeats26 if args.target in ['all','26.3'] else [])
    results=[run_target(mc,args.vendor) for mc in targets]
    raise SystemExit(any(result['status']!='PASS' for result in results))


if __name__=='__main__': main()
