"""Require affirmative evidence from production runs; missing runs are failures."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
results = []
for target, namespace, java in [("1214", "intermediary", 21), ("263", "official", 25)]:
    path = ROOT / ".evidence" / f"consumer-{target}-production.log"
    data = path.read_bytes() if path.exists() else b""
    encoding = "utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig"
    log = data.decode(encoding, errors="replace")
    lines = [line for line in log.splitlines() if "[FTE-POC]" in line]
    initialized = [line for line in lines if "INITIALIZED" in line]
    passed = [line for line in lines if "RUNTIME_PASS" in line]
    checks = {
        "exactly_one_initialization": len(initialized) == 1,
        "production_not_development": any("development=false" in line for line in initialized),
        "nested_sdk_origin": any("origin=NESTED" in line for line in initialized),
        "correct_java": any(f"java={java} " in line for line in initialized),
        "correct_namespace": any(f"namespace={namespace} " in line for line in initialized),
        "common_platform_and_core_called": any("probe=core[PoCConsumer/1]:shared-platform-ok" in line for line in initialized),
        "twenty_ticks_completed": len(passed) == 1 and f"ticks=20 namespace={namespace}" in passed[0],
        "gradle_successful_exit": "BUILD SUCCESSFUL" in log and "BUILD FAILED" not in log,
    }
    results.append({"target": "1.21.4" if target == "1214" else "26.3",
                    "status": "PASS" if all(checks.values()) else "FAIL",
                    "checks": checks, "markers": lines,
                    "warnings_errors": [line for line in log.splitlines()
                                        if re.search(r"/(?:WARN|ERROR)\]", line) or line.startswith("WARNING:")],
                    "log": str(path.relative_to(ROOT))})
out = ROOT / "results/runtime-checks.json"
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for item in results:
    print(item["target"], item["status"], item["checks"])
raise SystemExit(any(item["status"] != "PASS" for item in results))
