"""Inspect built/published artifacts without loading any SDK classes.

Run after pocPublishLocal and both independent consumer builds.
Uses the same optional maven.repo.local override as Maven/Gradle.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
GROUP = "net/funtimeevents/poc"
VERSION = "0.0.1-poc"
API_CLASS = "net/funtimeevents/poc/api/PocEventsAPI.class"
CORE_CLASS = "net/funtimeevents/poc/core/PocCore.class"
CHECKS: list[dict] = []


def check(name: str, condition: bool, detail: object) -> None:
    CHECKS.append({"check": name, "status": "PASS" if condition else "FAIL", "detail": detail})


def major(data: bytes) -> int:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Invalid class header")
    return struct.unpack_from(">H", data, 6)[0]


def inspect(mc: str, java: int, repo: Path) -> None:
    label = f"mc{mc}"
    artifact = repo / GROUP / label / VERSION
    jar = artifact / f"{label}-{VERSION}.jar"
    pom = artifact / f"{label}-{VERSION}.pom"
    check(f"{label}: published jar", jar.is_file(), str(jar))
    check(f"{label}: published pom", pom.is_file(), str(pom))
    if not jar.is_file() or not pom.is_file():
        return
    with zipfile.ZipFile(jar) as sdk:
        names = sdk.namelist()
        metadata = json.loads(sdk.read("fabric.mod.json"))
        check(f"{label}: one core copy", names.count(CORE_CLASS) == 1, names.count(CORE_CLASS))
        check(f"{label}: one platform copy", names.count(API_CLASS) == 1, names.count(API_CLASS))
        check(f"{label}: no bundled external libraries", not any(
            n.startswith(("com/google/gson/", "org/slf4j/", "net/fabricmc/", "net/minecraft/"))
            or n.endswith(".jar") for n in names), names)
        check(f"{label}: isolated mod id", metadata["id"] == "fte-multiversion-poc", metadata["id"])
        check(f"{label}: exact Minecraft", metadata["depends"]["minecraft"] == f"={mc}", metadata)
        check(f"{label}: Java constraint", metadata["depends"]["java"] == f">={java}", metadata)
        check(f"{label}: no template placeholders", "${" not in sdk.read("fabric.mod.json").decode(), metadata)
        check(f"{label}: core Java 21", major(sdk.read(CORE_CLASS)) == 65, major(sdk.read(CORE_CLASS)))
        platform = sdk.read(API_CLASS)
        check(f"{label}: platform bytecode", major(platform) == java + 44, major(platform))
        expected = b"net/minecraft/class_310" if java == 21 else b"net/minecraft/client/Minecraft"
        forbidden = b"net/minecraft/client/Minecraft" if java == 21 else b"net/minecraft/class_310"
        check(f"{label}: production namespace", expected in platform and forbidden not in platform,
              {"expected_reference": expected.decode(), "forbidden_reference": forbidden.decode()})
        check(f"{label}: core independent of game", b"net/minecraft/" not in sdk.read(CORE_CLASS)
              and b"net/fabricmc/" not in sdk.read(CORE_CLASS), "constant-pool package references")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    doc = ET.parse(pom).getroot()
    deps = [ET.tostring(d, encoding="unicode") for d in doc.findall("m:dependencies/m:dependency", ns)]
    check(f"{label}: POM no core dependency", not any("core" in d for d in deps), deps)
    module = artifact / f"{label}-{VERSION}.module"
    if module.is_file():
        module_data = json.loads(module.read_text(encoding="utf-8"))
        module_deps = [d for v in module_data.get("variants", []) for d in v.get("dependencies", [])]
        check(f"{label}: metadata no core dependency", not any(d.get("module") == "core" for d in module_deps), module_deps)
    else:
        check(f"{label}: Gradle module metadata", False, str(module))
    sources = artifact / f"{label}-{VERSION}-sources.jar"
    check(f"{label}: published sources", sources.is_file(), str(sources))
    consumer_dir = ROOT / "consumers" / label / "build" / "libs"
    consumers = [p for p in consumer_dir.glob(f"*{VERSION}.jar") if not any(
        marker in p.name for marker in ("-sources", "-dev"))]
    check(f"{label}: independent consumer built", len(consumers) == 1, [str(p) for p in consumers])
    if len(consumers) != 1:
        return
    with zipfile.ZipFile(consumers[0]) as consumer:
        metadata = json.loads(consumer.read("fabric.mod.json"))
        nested = [v["file"] for v in metadata.get("jars", [])]
        check(f"{label}: exactly one nested SDK", len(nested) == 1, nested)
        check(f"{label}: no flattened core", CORE_CLASS not in consumer.namelist(), consumer.namelist())
        check(f"{label}: runtime SDK dependency", metadata["depends"].get("fte-multiversion-poc") == "=" + VERSION, metadata)
        if len(nested) == 1:
            data = consumer.read(nested[0])
            check(f"{label}: nested equals published SDK", hashlib.sha256(data).digest() == hashlib.sha256(jar.read_bytes()).digest(),
                  {"nested_sha256": hashlib.sha256(data).hexdigest(), "published_sha256": hashlib.sha256(jar.read_bytes()).hexdigest()})
            with zipfile.ZipFile(io.BytesIO(data)) as child:
                check(f"{label}: nested core exists", CORE_CLASS in child.namelist(), child.namelist())


def main() -> None:
    global VERSION
    parser = argparse.ArgumentParser()
    parser.add_argument("--maven-local", type=Path, default=Path.home() / ".m2/repository")
    parser.add_argument("--version", default=VERSION)
    parser.add_argument("--targets", help="Comma separated Phase 5 targets, or all; omitted preserves the original two-target check")
    parser.add_argument("--output", type=Path, default=Path("results/artifact-checks.json"))
    args = parser.parse_args()
    VERSION = args.version
    targets=[("1.21.4",21),("26.3",25)]
    if args.targets:
        matrix=json.loads((ROOT/'targets.json').read_text())['targets']
        requested=[t['minecraft'] for t in matrix] if args.targets=='all' else args.targets.split(',')
        targets=[(mc,next(t['java'] for t in matrix if t['minecraft']==mc)) for mc in requested]
    for mc, java in targets:
        inspect(mc, java, args.maven_local)
    result = {"checks": CHECKS, "pass": sum(c["status"] == "PASS" for c in CHECKS),
              "fail": sum(c["status"] == "FAIL" for c in CHECKS)}
    out = ROOT / args.output
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for entry in CHECKS:
        print(entry["status"], entry["check"])
    print(f"Results: {out}; PASS={result['pass']}, FAIL={result['fail']}")
    raise SystemExit(bool(result["fail"]))


if __name__ == "__main__":
    main()
