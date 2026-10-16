#!/bin/sh
set -eu
task=/var/tmp/fte-sdk-phase5-01a13db2
mkdir -p "$task/lists/partial" "$task/debs" "$task/tools"
apt-get -o Dir::State::lists="$task/lists" update
cd "$task/debs"
apt-get -o Dir::State::lists="$task/lists" download xvfb xserver-common xauth libxfont2 libfontenc1 libunwind8 x11-xkb-utils \
 python3-aiohttp python3-yarl python3-async-timeout python3-aiohappyeyeballs \
 python3-aiosignal python3-attr python3-frozenlist python3-multidict python3-propcache python3-idna
sha256sum ./*.deb > "$task/tools/DEB_SHA256SUMS"
for archive in ./*.deb; do dpkg-deb -x "$archive" "$task/tools"; done
PYTHONPATH="$task/tools/usr/lib/python3/dist-packages" python3 -c 'import aiohttp; print(aiohttp.__version__)'
LD_LIBRARY_PATH="$task/tools/usr/lib/x86_64-linux-gnu" ldd "$task/tools/usr/bin/Xvfb" > "$task/tools/Xvfb-ldd.txt"
if grep -q 'not found' "$task/tools/Xvfb-ldd.txt"; then cat "$task/tools/Xvfb-ldd.txt"; exit 1; fi
LD_LIBRARY_PATH="$task/tools/usr/lib/x86_64-linux-gnu" "$task/tools/usr/bin/Xvfb" -help > "$task/tools/Xvfb-help.txt" 2>&1
for archive in ./*.deb; do rm -- "$archive"; done
