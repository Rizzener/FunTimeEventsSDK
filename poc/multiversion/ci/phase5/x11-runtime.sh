#!/bin/sh
set -eu
mc=$1
shift
export SDL_VIDEODRIVER=x11 SDL_VIDEO_DRIVER=x11 XDG_SESSION_TYPE=x11
unset WAYLAND_DISPLAY
export XDG_RUNTIME_DIR="${RUNNER_TEMP:-/tmp}/phase5-xdg-$$"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
export FTE_PHASE5_DISPLAY_RECEIPT="$PWD/${FTE_PHASE5_EVIDENCE:-results/phase5}/$mc/display-$$.json"
python3 ci/phase5/x11-ready.py
exec python3 phase5_run.py --targets "$mc" --kind runtime "$@"
