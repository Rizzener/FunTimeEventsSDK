"""Run real production Fabric server + Maven consumer, with bounded timeouts.

Prerequisite: SDK published and both consumers built. Build orchestration remains
in Gradle; this script only supervises the integration test processes.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parent

def maven_local() -> Path:
    return Path(os.environ.get('FTE_MAVEN_LOCAL',str(Path.home()/'.m2/repository'))).resolve()

def gradle(*arguments: str) -> list[str]:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    prefix = [shutil.which("rtk"), "proxy"] if shutil.which("rtk") else []
    isolated_maven=['-Dmaven.repo.local='+str(maven_local())] if os.environ.get('FTE_MAVEN_LOCAL') else []
    return prefix + [str(wrapper), "--no-daemon", "--console=plain", "--max-workers=2", *isolated_maven, *arguments]


def wait_port(port: int, process: subprocess.Popen, timeout: int) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Fixture server exited with {process.returncode}")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.5)
    raise TimeoutError("Fixture server startup timeout")


def spawn_owned(*args, **kwargs) -> subprocess.Popen:
    """Own a Linux process group, so a failed Gradle launcher cannot orphan the game."""
    if os.name != 'nt':kwargs['start_new_session'] = True
    process = subprocess.Popen(*args, **kwargs)
    process.fte_owned_group = process.pid if os.name != 'nt' else None
    return process


def capture_owned_threads(process: subprocess.Popen, output: Path) -> None:
    if os.name == 'nt' or not getattr(process, 'fte_owned_group', None):return
    for entry in Path('/proc').iterdir():
        if not entry.name.isdigit():continue
        pid=int(entry.name)
        try:
            if os.getpgid(pid)!=process.fte_owned_group:continue
            executable=(entry/'exe').resolve()
            if executable.name!='java':continue
            tool=executable.with_name('jcmd')
            if not tool.exists():continue
            with (output/('owned-thread-dump-'+str(pid)+'.txt')).open('wb') as log:
                subprocess.run([str(tool),str(pid),'Thread.print','-l'],stdout=log,stderr=subprocess.STDOUT,timeout=15)
        except (OSError,subprocess.TimeoutExpired) as error:
            with (output/'thread-dump-errors.txt').open('a') as log:log.write(str(pid)+': '+repr(error)+'\n')


def terminate_owned(process: subprocess.Popen) -> None:
    group=getattr(process,'fte_owned_group',None)
    if group:
        assert group==process.pid and group!=os.getpgrp()
        try:os.killpg(group,signal.SIGTERM)
        except ProcessLookupError:return
        try:process.wait(timeout=10)
        except subprocess.TimeoutExpired:pass
        try:os.killpg(group,signal.SIGKILL)
        except ProcessLookupError:pass
        return
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
    else:
        process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()


def run_target(mc: str) -> dict:
    port = 27414 if mc == "1.21.4" else 27633
    # Do not accidentally connect the test client to an unrelated process.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", port))
    output = ROOT / "results/phase2" / mc
    output.mkdir(parents=True, exist_ok=True)
    client_result = ROOT / "consumers" / f"mc{mc}" / "build/phase2-client/phase2-result.json"
    client_result.unlink(missing_ok=True)
    stop_file = ROOT / "targets" / f"mc{mc}" / "build/phase2-server/phase2.stop"
    client = None
    result = {"target": mc, "status": "FAIL", "server_command": gradle(f":mc{mc}:phase2Server"),
              "client_command": gradle("-p", f"consumers/mc{mc}", "prodClient", "-Pphase2")}
    with (output / "server.txt").open("wb") as server_log, (output / "client.txt").open("wb") as client_log:
        server = subprocess.Popen(result["server_command"], cwd=ROOT, stdout=server_log, stderr=subprocess.STDOUT)
        try:
            wait_port(port, server, 240)
            print(f"{mc}: fixture server listening on loopback:{port}", flush=True)
            client = subprocess.Popen(result["client_command"], cwd=ROOT, stdout=client_log, stderr=subprocess.STDOUT)
            result["client_exit"] = client.wait(timeout=300)
            if result["client_exit"] != 0 or not client_result.exists():
                raise RuntimeError(f"Client failed: exit={result['client_exit']}, result file={client_result.exists()}")
            observed = json.loads(client_result.read_text(encoding="utf-8"))
            result["runtime"] = observed
            if observed["status"] != "PASS":
                raise RuntimeError("Client assertion failure")
            result["status"] = "PASS"
        except Exception as error:
            result["error"] = repr(error)
        finally:
            if client is not None:
                terminate_owned(client)
            stop_file.parent.mkdir(parents=True, exist_ok=True)
            stop_file.touch()
            try:
                result["server_exit"] = server.wait(timeout=60)
            except subprocess.TimeoutExpired:
                result["server_shutdown"] = "timeout; terminated owned process tree"
                result["status"] = "FAIL"
                terminate_owned(server)
            if result.get("server_exit", -1) != 0:
                result["status"] = "FAIL"
    (output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(mc, result["status"], result.get("error", ""), flush=True)
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=["1.21.4", "26.3", "all"], default="all")
    args = parser.parse_args()
    targets = ["1.21.4", "26.3"] if args.target == "all" else [args.target]
    results = [run_target(target) for target in targets]
    raise SystemExit(any(item["status"] != "PASS" for item in results))


if __name__ == "__main__":
    main()
