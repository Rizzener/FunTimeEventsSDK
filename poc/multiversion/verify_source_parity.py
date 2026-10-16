"""Guard the migrated rules against the unchanged release SDK source."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
release = REPO / "fte-api/src/main/java/net/funtimeevents"
core = ROOT / "core/src/main/java/net/funtimeevents"
old_ban = (release / "tracker/ban/BanTracker.java").read_text(encoding="utf-8")
new_ban = (core / "poc/slice/BanTracker.java").read_text(encoding="utf-8")
old_server = (release / "tracker/server/ServerContext.java").read_text(encoding="utf-8")
new_server = (core / "poc/slice/ServerContext.java").read_text(encoding="utf-8")
checks = []


def check(name, value):
    checks.append({"check": name, "status": "PASS" if value else "FAIL"})


def pattern(source, name):
    found = re.search(r"\b" + name + r"\s*=\s*Pattern\.compile\((.*?)\);", source, re.S)
    if not found:
        raise ValueError(name)
    return found[1].strip()


def method(source, name):
    found = re.search(r"static (?:String|int) " + name + r"\([^)]*\)\s*\{", source)
    if not found:
        raise ValueError(name)
    start = found.end()
    depth = 1
    for index in range(start, len(source)):
        depth += (source[index] == "{") - (source[index] == "}")
        if not depth:
            return re.sub(r"\s+", "", source[start:index])
    raise ValueError("Unclosed method: " + name)


for name in ["BAN_PATTERN", "HOVER_REASON_PATTERN", "HOVER_END_PATTERN", "HOVER_SERVER_PATTERN"]:
    check("unchanged pattern " + name, pattern(old_ban, name) == pattern(new_ban, name))
for name in ["parseReason", "parseEnd", "parseServerId"]:
    check("unchanged helper " + name, method(old_ban, name) == method(new_ban, name))
for name in ["FUN_TIME_PATTERN", "SERVER_ID_PATTERN"]:
    check("unchanged server pattern " + name, pattern(old_server, name) == pattern(new_server, name))
for relative in ["model/BanPayload.java", "util/GsonHolder.java"]:
    check("identical source " + relative, (release / relative).read_bytes() == (core / relative).read_bytes())
check("same server type", 'return "anarchy";' in new_server and 'serverType = "anarchy"' in old_server)
check("no reflection in phase2 Java", not any(
    marker in file.read_text(encoding="utf-8")
    for base in [core / "poc/slice", ROOT / "platform", ROOT / "fixtures/server", ROOT / "consumers/common/src"]
    for file in base.rglob("*.java")
    for marker in ["java.lang.reflect", "Class.forName(", ".getDeclaredField(", ".getMethod("]
))
check("runtime fixtures do not invoke parsers or Fabric events", not any(
    marker in file.read_text(encoding="utf-8")
    for base in [ROOT / "fixtures/server", ROOT / "consumers/common/src"]
    for file in base.rglob("*.java")
    for marker in [".invoker()", ".handleMessage(", ".parseReason(", ".parseEnd(", ".parseServerId("]
))
result = {"checks": checks, "release_sources_sha256": {
    relative: hashlib.sha256((release / relative).read_bytes()).hexdigest()
    for relative in ["tracker/ban/BanTracker.java", "tracker/server/ServerContext.java", "model/BanPayload.java", "util/HoverEventUtil.java"]
}}
output = ROOT / "results/phase2-source-parity.json"
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for item in checks:
    print(item["status"], item["check"])
raise SystemExit(any(item["status"] != "PASS" for item in checks))
