#!/bin/sh
set -eu
chmod +x gradlew
sudo apt-get update
sudo apt-get install -y --no-install-recommends xvfb xauth x11-xkb-utils xkb-data x11-utils libgl1-mesa-dri libglx-mesa0 libgl1 \
 libxi6 libxrandr2 libxcursor1 libxinerama1 libxtst6 libasound2t64 libfontconfig1 libfreetype6
python3 ci/phase5/install_jdks.py --pins ci/phase5/jdks.json --destination "$RUNNER_TEMP/phase5-jdks"
python3 -m venv "$RUNNER_TEMP/phase5-python"
"$RUNNER_TEMP/phase5-python/bin/pip" install -r fixtures/requirements-phase4.txt
mkdir -p "$HOME/.gradle"
printf 'org.gradle.java.installations.paths=%s/21,%s/25\norg.gradle.java.installations.auto-download=false\norg.gradle.java.installations.auto-detect=false\n' "$RUNNER_TEMP/phase5-jdks" "$RUNNER_TEMP/phase5-jdks" > "$HOME/.gradle/gradle.properties"
printf 'JAVA_HOME=%s/25\n' "$RUNNER_TEMP/phase5-jdks" >> "$GITHUB_ENV"
printf '%s/25/bin\n%s/bin\n' "$RUNNER_TEMP/phase5-jdks" "$RUNNER_TEMP/phase5-python" >> "$GITHUB_PATH"
mkdir -p results/phase6
dpkg-query -W xvfb libgl1-mesa-dri > results/phase6/ci-system-packages.txt
