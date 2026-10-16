ARG BASE=ubuntu:24.04
FROM ${BASE}
LABEL net.funtimeevents.phase="5" purpose="isolated SDK verification only"
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl git python3 python3-venv python3-pip xauth xvfb mesa-utils \
    libgl1-mesa-dri libglx-mesa0 libgl1 libx11-6 libxrender1 libxi6 libxrandr2 \
    libxcursor1 libxinerama1 libxtst6 libasound2t64 libpulse0 libfontconfig1 libfreetype6 \
    libdbus-1-3 fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
COPY jdks.json install_jdks.py /opt/phase5/
RUN python3 /opt/phase5/install_jdks.py
ENV JAVA_HOME=/opt/jdks/25 PATH=/opt/jdks/25/bin:${PATH}
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
RUN python3 -m venv /opt/phase5/venv && /opt/phase5/venv/bin/pip install --no-cache-dir aiohttp==3.12.15 cryptography==44.0.3
ENV PATH=/opt/phase5/venv/bin:${PATH} LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
RUN mkdir -p /root/.gradle && printf 'org.gradle.java.installations.paths=/opt/jdks/21,/opt/jdks/25\norg.gradle.java.installations.auto-download=false\n' > /root/.gradle/gradle.properties
WORKDIR /repo/poc/multiversion
