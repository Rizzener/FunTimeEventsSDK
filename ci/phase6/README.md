# Release candidate CI

`.github/workflows/release-candidate.yml` runs thirteen independent hosted jobs.
Each job compiles core and the chosen target, publishes to a local staging Maven
repository, builds consumers in separate Gradle projects, verifies artifacts and
public descriptors, then runs real production Fabric clients/servers under Xvfb.
All four runtime modes and REST/WSS/SSE/Captcha fixtures are mandatory. 26.3 has
two additional REST startups. Failures retain logs and JSON; no existing PASS is
substituted for another target, commit, workflow run or workflow attempt.

`SDK required matrix` checks all job conclusions, thirteen provenance receipts
and the full functional summary. Configure that exact check as required only
after reviewing a successful hosted run. No branch protection is changed here.

An additional all-targets job runs `sdkBuild sdkStage` without target filtering.
The required job compares all 52 Maven files against independent target outputs.
JDK autodetection is disabled in ephemeral CI: only the two hash-pinned installations
may satisfy toolchains. Provenance also checks actual production runtime versions.

Pins: targets.json, ci/phase5/jdks.json and ci/phase5/action-pins.json. Historical
phase5 helper names are retained for traceability. Candidate artifacts are uploaded
as workflow artifacts; no Maven deployment credentials or remote publication task
exists. A workflow artifact is staging evidence, not a public SDK release.

Run locally on Linux with JDK 21/25, Python dependencies from
fixtures/requirements-phase4.txt and Xvfb:

```sh
export FTE_PHASE5_EVIDENCE=results/phase6/local-clean
export FTE_MAVEN_LOCAL="$PWD/.evidence/phase6/local-clean-maven"
chmod +x gradlew
sh ci/phase6/job.sh 1.21.4
```

Use a new evidence directory and a fresh staging repository for a new candidate.
Immutable publication guards intentionally reject reusing a published version.
Never enable public publication or change the primary channel as a CI workaround.

MC26.3 requires a usable real graphics backend even under Xvfb. The Ubuntu setup
installs standard Mesa OpenGL and Vulkan ICD packages; the game's own backend
selection is unchanged. `graphics_check.py` records glxinfo/vulkaninfo and probes
the exact Mojang SDL native library. Its sRGB-disabled probe is diagnostic only;
it never changes the production game. A graphics probe alone is not runtime PASS.

`phase6_process_controls.py` proves descendant cleanup and preservation of an
unrelated control process. Linux fixtures own a separate process group. A runtime
timeout retains available thread dumps and fails immediately; remaining modes
stay NOT RUN. Historical failed attempts and native diagnostics remain evidence.
