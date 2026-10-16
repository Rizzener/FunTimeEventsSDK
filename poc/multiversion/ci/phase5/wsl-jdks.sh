#!/bin/sh
set -eu
task=/var/tmp/fte-sdk-phase5-01a13db2
cd "$task/debs"
apt-get -o Dir::State::lists="$task/lists" download \
 openjdk-21-jdk-headless=21.0.12.1+1-1~26.04.4 openjdk-21-jre-headless=21.0.12.1+1-1~26.04.4 \
 openjdk-25-jdk-headless=25.0.4.1+1-1~26.04.4 openjdk-25-jre-headless=25.0.4.1+1-1~26.04.4 \
 openjdk-21-jre=21.0.12.1+1-1~26.04.4 openjdk-25-jre=25.0.4.1+1-1~26.04.4 libfontenc1
sha256sum ./*.deb > "$task/tools/JDK_DEB_SHA256SUMS"
for archive in ./*.deb; do dpkg-deb -x "$archive" "$task/tools"; done
python3 - <<'PY'
from pathlib import Path
root=Path('/var/tmp/fte-sdk-phase5-01a13db2/tools')
for path in (root/'usr/lib/jvm').rglob('*'):
    if not path.is_symlink():continue
    target=path.readlink()
    if target.is_absolute() and (root/str(target).lstrip('/')).exists():
        path.unlink();path.symlink_to(root/str(target).lstrip('/'))
PY
"$task/tools/usr/lib/jvm/java-21-openjdk-amd64/bin/javac" -version
"$task/tools/usr/lib/jvm/java-25-openjdk-amd64/bin/javac" -version
LD_LIBRARY_PATH="$task/tools/usr/lib/x86_64-linux-gnu" ldd "$task/tools/usr/bin/Xvfb"
for archive in ./*.deb; do rm -- "$archive"; done
