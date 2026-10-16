#!/bin/sh
# Extract task-local tools. dpkg's system database and broken Java are untouched.
set -eu
task=/var/tmp/fte-sdk-phase5-01a13db2
mkdir -p "$task/debs" "$task/tools"
cd "$task/debs"
apt-get download openjdk-21-jdk-headless openjdk-21-jre-headless \
 openjdk-25-jdk-headless=25.0.4+7-1~26.04 openjdk-25-jre-headless=25.0.4+7-1~26.04 \
 xvfb=2:21.1.22-1ubuntu1 xserver-common xauth libxfont2 x11-xkb-utils \
 python3-aiohttp=3.13.3-3ubuntu1 python3-yarl python3-async-timeout python3-aiohappyeyeballs \
 python3-aiosignal python3-attr python3-frozenlist python3-multidict python3-propcache python3-idna
sha256sum ./*.deb > "$task/tools/DEB_SHA256SUMS"
for archive in ./*.deb; do dpkg-deb -x "$archive" "$task/tools"; done
"$task/tools/usr/lib/jvm/java-21-openjdk-amd64/bin/javac" -version
"$task/tools/usr/lib/jvm/java-25-openjdk-amd64/bin/javac" -version
PYTHONPATH="$task/tools/usr/lib/python3/dist-packages" python3 -c 'import aiohttp; print(aiohttp.__version__)'
LD_LIBRARY_PATH="$task/tools/usr/lib/x86_64-linux-gnu" "$task/tools/usr/bin/Xvfb" -help 2>&1 | head -n 4
# Only the archives just downloaded by this script; their hashes are retained.
for archive in ./*.deb; do rm -- "$archive"; done
