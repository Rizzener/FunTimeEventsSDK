#!/bin/sh
# Runs only inside unshare --mount. No global WSL package/configuration changes.
set -eu
runner=$1
shift
workspace=${5:-native}
case "$workspace" in native|native-b) ;; *) exit 2 ;; esac
task=/var/tmp/fte-sdk-phase5-01a13db2
root=$task/$workspace
mount --make-rprivate /
mkdir -p "$root/x11-sockets" "$root/x11-bin" "$root/x11-bin-work" "$root/xdg"
chmod 1777 "$root/x11-sockets"
chmod 700 "$root/xdg"
if [ ! -e "$root/x11-bin/xkbcomp" ]; then ln -s "$task/tools/usr/bin/xkbcomp" "$root/x11-bin/xkbcomp"; fi
mount -t overlay overlay -o "lowerdir=/usr/bin,upperdir=$root/x11-bin,workdir=$root/x11-bin-work" /usr/bin
mount --bind "$root/x11-sockets" /tmp/.X11-unix
export XDG_RUNTIME_DIR="$root/xdg" XDG_SESSION_TYPE=x11 SDL_VIDEODRIVER=x11 SDL_VIDEO_DRIVER=x11
export FTE_REQUIRE_PRIVATE_XVFB=1
unset WAYLAND_DISPLAY XAUTHORITY
exec /bin/sh "$runner" "$@"
