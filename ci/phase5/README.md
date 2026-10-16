# Phase 5 verification jobs

This directory is isolated from release CI. `matrix.yml` is a prepared GitHub
Actions workflow template; no workflow was dispatched and no SDK code was sent
to an external host. The user authorized **local Windows/WSL checks only**.

The matrix reads all 13 releases from `targets.json`. Jobs are separated into
compile/core verification, publication to a test Maven repository + Maven Local,
independent consumer/artifact/API verification, real production runtime and
network verification, and a fail-closed summary. Missing required results are
`NOT VERIFIED`. `github.run_id` prevents old committed evidence being counted
as a new CI run. Action SHAs were resolved through official GitHub API; exact
refs are retained in `action-pins.json`. Java archives have pinned SHA256.

To activate later, copy `matrix.yml` to the repository's `.github/workflows/`
after the separate integration decision. The template itself is not evidence
of a hosted run. Its syntax is checked locally; actual stages are exercised
through the same Python/Gradle commands in the local WSL runner.

Local Windows:

```text
python phase5_build.py --targets all --kind compile
python phase5_build.py --targets all --kind publish
python phase5_build.py --targets all --kind consumer
python phase5_api.py --targets all
python phase5_verify.py --targets all --stage artifacts
python prepare_phase4_tls.py
python phase5_run.py --targets all --kind network
python phase5_verify.py --targets all --stage network
python phase5_run.py --targets all --kind runtime
python phase5_verify.py --targets all --stage runtime
python phase5_report.py
```

After both local matrix supervisors have exited and collection is complete:

```text
python phase5_final_verify.py --workers-finished
python phase5_manifest.py
python phase5_manifest.py --verify
```

The final verifier records each command/exit code and rechecks stable collected
evidence. Do not run final aggregation concurrently with a WSL copy refresh;
the observed transient NOT VERIFIED result and stable recheck are preserved
in `PHASE5_TEST_FIXES.md`.

The additional 26.2 performance controls retain the original 2 CPU outlier.
They add ThreadMXBean CPU timing only to the consumer fixture, then repeat full
REST runtime at task CPU quotas 2 and 4. Exact local WSL commands and exits are
saved in `results/phase5/local-environment/performance-cpu-control/runner.json`.
They use `FTE_PHASE5_THREAD_CPU=1` and `FTE_PHASE5_CPU_LIMIT=2|4` before
`wsl-limit.py ... wsl-run.sh runtime 26.2 rest perf-cpu2|perf-cpu4 native`.
`python phase5_perf_control.py` checks the same SDK hash, full runtime verdicts
and exports all long wall/CPU spans. These are diagnostic resource controls,
not SDK changes or a claim that the native startup issue is fixed.

Publication refuses to overwrite a version already present in Maven Local.
Use a new, empty Maven repository/home for a clean reproduction, or a new
experimental version. Never delete or overwrite a previously published SDK.

Local WSL:

```text
python phase5_wsl.py --action prepare
```

That first command prepares the immutable Windows seed. The current runner uses
native Linux files, so a fresh local Ubuntu workspace also needs these steps
(all paths below refer to this machine, not a remote host):

```text
wsl -d Ubuntu -u root -- sh /mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-refresh.sh
wsl -d Ubuntu -u root -- sh /mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-jdks.sh
wsl -d Ubuntu -u root -- sh /mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-x11-deps.sh
wsl -d Ubuntu -u root -- python3 /mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-native.py
python phase5_wsl.py --action run --kind compile --targets 1.21.5
python phase5_wsl.py --action run --kind publish --targets 1.21.5
python phase5_wsl.py --action run --kind consumer --targets 1.21.5
python phase5_wsl.py --action run --kind network --targets 1.21.5
python phase5_wsl.py --action run --kind display-probe --targets 1.21.5
python phase5_wsl.py --action run --kind runtime --targets 1.21.5
python phase5_wsl.py --action run --kind matrix --label local --targets all
```

`wsl-portable.sh` and `wsl-dependencies.sh` retain earlier failed preparation
attempts; use the later refresh/JDK scripts above. They download/extract local
Ubuntu tools and do not repair the interrupted system dpkg database. A fresh
standard Ubuntu CI runner should use `bootstrap-ci.sh` instead.

Optional native assets preparation, only while no production client is running:

```text
python ci/phase5/wsl-assets.py --action package
wsl -d Ubuntu -u root -- python3 /mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-assets.py --action prepare
wsl -d Ubuntu -u root -- python3 /mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-assets.py --action activate
```

The transfer validates all objects and rejects indexes that fail pinned SHA-1.
The original Windows assets link is retained separately. See
`PHASE5_TEST_FIXES.md` for the failed index attempt and Linux TIME_WAIT probe fix.

An optional second local worker uses a separate source/home/Maven/Gradle tree,
game ports starting at 35040, and a different Xvfb display range:

```text
wsl -d Ubuntu -u root -- python3 /mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-native.py --worker-b
python phase5_wsl.py --action run --kind matrix --workspace native-b --label local-b --targets 26.1.2,26.1.1,1.21.10,1.21.9,1.21.7,1.21.6
python phase5_linux_results.py
```

It hard-links only hash-verified immutable inputs from the first native worker;
candidate SDK outputs are never seeded. Run it after Windows production lanes
finish on this 16 GiB host. Aggregation requires one complete worker result per
target and retains the original evidence prefix; it does not combine incomplete
stage results into a PASS.

WSL uses a separate source tree, HOME, Maven repository and mutable Gradle
state. Hash-manifested immutable dependency artifacts/assets and official raw
Minecraft inputs are hard-linked to conserve C: disk space. Candidate SDK
outputs and Windows-generated Minecraft outputs are never seeded. WSL system
dpkg was already interrupted; portable official Ubuntu packages were extracted
under `/var/tmp/fte-sdk-phase5-01a13db2/tools`, without repairing/reconfiguring
the system Java packages. The pinned Ubuntu JDK versions differ from hosted
Temurin pins; raw logs state the exact JVM used for each check.

On this 16 GiB machine, run new build jobs sequentially alongside at most one
production lane. Preserve timeouts/failures and retry with unchanged assertions
after contention has ended. Resource exhaustion does not count as functional
PASS. Xvfb/Mesa are test environment components, not SDK native-crash fixes.

Every Linux runtime now requires a successful `xdpyinfo` display receipt.
`python phase5_headless_check.py` exercises missing/incomplete/false receipts
against the actual verifier and is also part of the prepared compile CI stage.
The local WSL runner uses a private mount namespace with task-local X11 sockets
and the real official `xkbcomp`; it disables WSLg fallback without changing
global packages, mounts, or JVM flags. Earlier Linux process successes without
this receipt remain in raw evidence and do not count as verified headless runs.
See `PHASE5_TEST_FIXES.md` and `local-environment/x11-display-probes.json`.

The main runner calls `phase5_space.py` only after a target's processes exit.
It removes only regenerable game inputs/worlds; raw logs, JSON, SDK artifacts
and crash diagnostics remain. `phase5_watch.py` is retained as historical
infrastructure and was disabled after concurrent cleanup raced with the main
runner. Do not run both cleanup owners or clean a live target.

Native 26.3 startup investigation stays **OPEN**, including after successful
new runs. Hosted startup checks add two full production repeats for 26.3;
the same command can be run locally with `--modes rest --repeats 2`.
