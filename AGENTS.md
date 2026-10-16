@C:\Users\andre\.codex\RTK.md

# FunTimeEventsSDK — development guide

## Architecture and build

One Gradle multi-project: `core` (Java 21, no game/Fabric), `platform/common`
(real public API, transports and trackers), eight small version leaves, thirteen
`targets/mc*`. `targets.json` pins every target. Do not infer support for untested
releases. Internal packages use `internal.*`; preserve existing public signatures.
Core is flattened into each SDK JAR once, with no Maven dependency on `core`.

Historical accepted baseline: `poc/multiversion` and commit `d3a4f1e`.
Previous release source: commit `99948f1`. Do not edit historical PoC inputs or
results. No tracker/transport copies between targets; no compatibility reflection.

Install JDK 21 and 25. Run Gradle 9.6.1 on JDK 25. Toolchains select Java 21 for
1.21.4–1.21.11 and Java 25 for 26.1–26.3. Loom 1.17.21 is pinned. Legacy uses
Mojang mappings + remap plugin + remapJar (intermediary); modern is unobfuscated.

```powershell
.\gradlew :core:check -Ptarget_versions=1.21.4
.\gradlew :mc1.21.4:build -Ptarget_versions=1.21.4
.\gradlew sdkBuild
.\gradlew sdkStage -Ptarget_versions=1.21.4
.\gradlew sdkPublishLocal -Ptarget_versions=1.21.4
```

`-Pmod_version` and `-Pmaven_group` override targets.json defaults. `sdkStage`
writes only to build/staging-maven. No remote Maven publication is configured.
Never overwrite a tested version. Consumers are independent Gradle builds and
resolve Maven artifacts, not producer project dependencies. Multi-project Loom
works here; the old blanket prohibition on composite/multi-project builds is obsolete.

## Regression

```powershell
python phase5_build.py --targets 1.21.4 --kind compile
python phase5_build.py --targets 1.21.4 --kind publish
python compile_legacy_probe.py
python phase5_build.py --targets 1.21.4 --kind consumer
python phase5_verify.py --targets 1.21.4 --stage artifacts
python phase5_api.py --targets 1.21.4
python prepare_phase4_tls.py
python phase5_run.py --targets 1.21.4 --kind network
python phase5_run.py --targets 1.21.4 --kind runtime
```

Retained harness names identify their origin; root defaults now write Phase 6
evidence and use release target coordinates. `--targets all` covers the registry.
Linux production requires real Xvfb/xdpyinfo: use ci/phase5/x11-runtime.sh.
Hosted CI runs ci/phase6/job.sh; missing/failed targets fail SDK required matrix.
Evidence path includes workflow run and attempt; provenance includes commit SHA.
Keep failed attempts, raw logs, golden fixtures, hashes and native diagnostics.
Historical PASS never fills a new candidate matrix cell.
`python tools/phase6_docs.py` regenerates docs and the standalone example's version
catalog/wrapper from the root registry. CI compiles a detached copy of that example
using its own wrapper, and rejects a stale catalog or hidden Maven Local fallback.

## Threading, lifecycle and delivery

Client thread owns game state. Netty DISCONNECT uses identity-aware handoff;
JOIN/tick/messages drain it. Runtime and game-session lifetimes differ. One Fabric
bridge stays registered; shutdown detaches runtime. Shared mutable state needs
ownership, locks, atomics or immutable publication; volatile alone is insufficient
for compound operations or mutable collections.

Trackers use PayloadSender; Captcha always uses REST. Preserve patterns, zones,
intervals and wire formats. Use GsonHolder and module-tagged FteLogger calls.
JSON/gzip and I/O run off the client thread. Snapshots are coalesced/short-lived;
FIFO events remain bounded best effort without backend ACK. Do not incidentally
change backend protocols or runtime ownership. Full Builder configurations must
match to share a singleton; conflicts throw without exposing credentials.
Legacy static shutdown is global: consumer mods must not call it on disconnect.

Native startup 26.3 remains OPEN. No unverified JVM or driver workaround.

## Release boundary

Candidate branch only. No public Maven publication, tag, GitHub Release or merge
without separate user approval. JitPack remains the current channel. Test staging
is local to each runner. SDK JARs must exclude experimental APIs, fixtures, test
keys and certificates. See PHASE6_RESULTS.md for the actual verification state.
