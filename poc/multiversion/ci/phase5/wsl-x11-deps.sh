#!/bin/sh
set -eu
task=/var/tmp/fte-sdk-phase5-01a13db2
mkdir -p "$task/x11-debs" "$task/tools"
cd "$task/x11-debs"
apt-get -o Dir::State::lists="$task/lists" download x11-xkb-utils xkb-data libxkbfile1 x11-utils libxxf86dga1
sha256sum ./*.deb > "$task/tools/X11_DEB_SHA256SUMS"
for archive in ./*.deb; do dpkg-deb -f "$archive" Package Version; dpkg-deb -x "$archive" "$task/tools"; done
export LD_LIBRARY_PATH="$task/tools/usr/lib/x86_64-linux-gnu"
ldd "$task/tools/usr/bin/xkbcomp" > "$task/tools/xkbcomp-ldd.txt"
ldd "$task/tools/usr/bin/xdpyinfo" > "$task/tools/xdpyinfo-ldd.txt"
if grep -q 'not found' "$task/tools/xkbcomp-ldd.txt" "$task/tools/xdpyinfo-ldd.txt"; then
  cat "$task/tools/xkbcomp-ldd.txt" "$task/tools/xdpyinfo-ldd.txt"; exit 1
fi
"$task/tools/usr/bin/xkbcomp" -version
