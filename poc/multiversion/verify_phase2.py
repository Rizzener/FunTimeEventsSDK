"""Independent check of runtime evidence, wire fixtures and slice packaging."""
from pathlib import Path
import hashlib
import json
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parent
VERSION = "0.0.4-phase2"
sessions = json.loads((ROOT / "fixtures/data/phase2/ban-sessions.json").read_text(encoding="utf-8"))
expected_payloads = [a["payload"] for s in sessions for a in s["actions"]
                     if a["kind"] == "message" and a["payload"] is not None]
expected_callbacks = [(index, a) for index, s in enumerate(sessions, 1)
                      for a in s["actions"] if a["kind"] == "message"]
summary = []
for mc, java, namespace in [("1.21.4", 21, "intermediary"), ("26.3", 25, "official")]:
    directory = ROOT / "results/phase2" / mc
    observed = json.loads((directory / "result.json").read_text(encoding="utf-8"))
    runtime = observed.get("runtime", {})
    server_log = (directory / "server.txt").read_text(encoding="utf-8", errors="replace")
    client_log = (directory / "client.txt").read_text(encoding="utf-8", errors="replace")
    checks = {}

    def check(name, condition):
        checks[name] = bool(condition)

    check("both_processes_exited_normally", observed.get("client_exit") == 0 and observed.get("server_exit") == 0)
    check("positive_runtime_result", observed["status"] == "PASS" and runtime.get("status") == "PASS")
    check("production_nested_correct_java_namespace", runtime.get("development") is False
          and runtime.get("origin") == "NESTED" and runtime.get("java") == java and runtime.get("namespace") == namespace)
    check("tested_sdk_version", f"fte-multiversion-poc {VERSION}" in client_log)
    check("four_joins_four_disconnects_one_binding", runtime.get("joins") == 4 and runtime.get("disconnects") == 4 and runtime.get("bindings") == 1)
    check("lifecycle_order_and_cleared_state", runtime.get("lifecycle") == [item for index in range(1, 5)
          for item in [f"JOIN {index}", f"DISCONNECT {index} cleared"]])
    threads = runtime.get("disconnect_threads", [])
    check("disconnects_processed_on_client_thread", len(threads) == 4 and all(t["handled"] == "Render thread" for t in threads))
    check("exact_legacy_payloads", runtime.get("payloads") == expected_payloads)
    callbacks = runtime.get("callbacks", [])
    check("every_wire_fixture_received_once", len(callbacks) == len(expected_callbacks)
          and [(row["session"], row["id"]) for row in callbacks] == [(i, a["id"]) for i, a in expected_callbacks])
    check("hover_and_plain_text_preserved", len(callbacks) == len(expected_callbacks) and all(
        row["channel"] == a["channel"] and a["text"] in row["plain"] and row["hover"] == a.get("hover", "")
        for row, (_, a) in zip(callbacks, expected_callbacks)))
    check("both_fabric_message_channels", {r["channel"] for r in callbacks} == {"GAME", "CHAT"})
    index = {(r["session"], r["id"]): r for r in callbacks}
    check("no_previous_server_before_new_sidebar", all(index.get(key, {}).get("server_id") == -1
          for key in [(1, "unresolved-first"), (2, "no-stale-104"), (4, "no-stale-306")]))
    check("non_funtime_host_rejected", index.get((3, "non-funtime-gate"), {}).get("on_funtime") is False)
    check("sidebar_update_observed", index.get((2, "sidebar-change"), {}).get("server_id") == 206)
    wire_sent = re.findall(r"\[PHASE2-SERVER\] SEND session=(\d+) id=(\S+) channel=(\w+)", server_log)
    check("dedicated_server_sent_exact_fixtures", wire_sent == [(str(i), a["id"], a["channel"]) for i, a in expected_callbacks])
    check("real_server_joined_four_times", len(re.findall(r"\[PHASE2-SERVER\] JOIN session=", server_log)) == 4)
    check("gradle_client_and_server_success", "BUILD SUCCESSFUL" in server_log and "BUILD SUCCESSFUL" in client_log)
    sdk = Path.home() / ".m2/repository/net/funtimeevents/poc" / f"mc{mc}" / VERSION / f"mc{mc}-{VERSION}.jar"
    with zipfile.ZipFile(sdk) as jar:
        names = jar.namelist()
        classes = ["net/funtimeevents/poc/slice/BanTracker.class", "net/funtimeevents/poc/slice/ServerContext.class",
                   "net/funtimeevents/poc/slice/BanSliceRuntime.class", "net/funtimeevents/model/BanPayload.class"]
        check("one_copy_of_slice_classes", all(names.count(name) == 1 for name in classes))
        check("no_other_trackers", not any(any(other in name for other in ["DungeonTracker", "MineTracker", "HellMapTracker", "TabTracker", "EventCoordinatesTracker"]) for name in names))
        check("no_fixtures_in_sdk", not any(name.startswith(("phase2/", "net/funtimeevents/poc/fixture/")) for name in names))
        check("one_selected_hover_adapter", names.count("net/funtimeevents/poc/platform/HoverAccess.class") == 1)
        check("core_slice_java21_no_minecraft", all(struct.unpack_from(">H", jar.read(name), 6)[0] == 65
              and b"net/minecraft/" not in jar.read(name) and b"net/fabricmc/" not in jar.read(name) for name in classes))
    summary.append({"target": mc, "status": "PASS" if all(checks.values()) else "FAIL", "checks": checks,
                    "callbacks": len(callbacks), "payloads": len(runtime.get("payloads", [])),
                    "disconnect_threads": threads, "sdk_sha256": hashlib.sha256(sdk.read_bytes()).hexdigest(),
                    "warnings_errors": {"server": [l for l in server_log.splitlines() if "/ERROR]" in l or "/WARN]" in l],
                                        "client": [l for l in client_log.splitlines() if "/ERROR]" in l or "/WARN]" in l]}})
out = ROOT / "results/phase2-summary.json"
out.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for row in summary:
    print(row["target"], row["status"], f"{sum(row['checks'].values())}/{len(row['checks'])}",
          "payloads", row["payloads"], "callbacks", row["callbacks"])
    for name, passed in row["checks"].items():
        if not passed: print("FAIL", name)
raise SystemExit(any(row["status"] != "PASS" for row in summary))
