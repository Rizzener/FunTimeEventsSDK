#!/bin/sh
set -eu
workspace=${5:-native}
case "$workspace" in native|native-b) ;; *) exit 2 ;; esac
root=/var/tmp/fte-sdk-phase5-01a13db2/$workspace
tools=/var/tmp/fte-sdk-phase5-01a13db2/tools
export HOME="$root/home" GRADLE_USER_HOME="$root/gradle"
export JAVA_HOME="$tools/usr/lib/jvm/java-25-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$tools/usr/bin:$PATH"
export LD_LIBRARY_PATH="$tools/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export PYTHONPATH="$tools/usr/lib/python3/dist-packages"
export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
export FTE_PHASE5_EVIDENCE=results/phase5/wsl/$workspace
export FTE_PHASE5_COLLECT=/mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/results/phase5/wsl/$workspace
export FTE_PHASE5_WORKSPACE="$root"
if [ "$workspace" = native-b ]; then export FTE_PHASE5_GAME_PORT_BASE=35040; fi
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
collect() {
  python3 - <<'PY'
import shutil,os
from pathlib import Path
source=Path(os.environ['FTE_PHASE5_WORKSPACE'])/'repo/poc/multiversion/results/phase5/wsl'
dest=Path('/mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/results/phase5/wsl')
if source.exists():shutil.copytree(source,dest,dirs_exist_ok=True)
PY
}
trap collect EXIT
cd "$root/repo/poc/multiversion"
case "$1" in
 compile|publish|consumer) python3 -u -X utf8 phase5_build.py --kind "$1" --targets "$2" ;;
 network) python3 prepare_phase4_tls.py; python3 -u -X utf8 phase5_run.py --kind network --targets "$2" ;;
 runtime|matrix|display-probe)
   python3 prepare_phase4_tls.py
   unset WAYLAND_DISPLAY
   offset=150
   if [ "$workspace" = native-b ]; then offset=2150; fi
   display=:$((offset + $$ % 1000))
   Xvfb "$display" -screen 0 1024x768x24 -nolisten tcp -xkbdir "$tools/usr/share/X11/xkb" > "$root/xvfb.log" 2>&1 &
   owner=$!;trap 'kill "$owner" 2>/dev/null || true; collect' EXIT INT TERM
   export DISPLAY="$display"
   export FTE_PHASE5_DISPLAY_RECEIPT="$root/headless-display.json"
   python3 "$root/repo/poc/multiversion/ci/phase5/x11-ready.py"
   if [ "$1" = display-probe ]; then exit 0; fi
   if [ "$1" = matrix ]; then
     python3 -u -X utf8 phase5_linux_matrix.py --targets "$2" --run-label "${4:-matrix}"
   else
     python3 -u -X utf8 phase5_run.py --kind runtime --targets "$2" --modes "$3"
   fi
   ;;
 *) exit 2 ;;
esac
