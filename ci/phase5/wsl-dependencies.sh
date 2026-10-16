#!/bin/sh
# Local, explicitly authorized WSL checks. No remote host or host JVM settings.
set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get install -y --no-install-recommends openjdk-25-jdk-headless=25.0.4+7-1~26.04 \
 xvfb=2:21.1.22-1ubuntu1 xauth python3-aiohttp=3.13.3-3ubuntu1 \
 libxi6 libxrandr2 libxcursor1 libxinerama1 libxtst6 libasound2t64 libgl1 libglx-mesa0 libgl1-mesa-dri
uname -a
cat /etc/os-release
/usr/lib/jvm/java-21-openjdk-amd64/bin/java -version
/usr/lib/jvm/java-25-openjdk-amd64/bin/java -version
dpkg-query -W openjdk-21-jdk-headless openjdk-25-jdk-headless xvfb python3-aiohttp
