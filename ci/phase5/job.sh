#!/bin/sh
# Invoked from poc/multiversion after Java 21/25 and Python fixture deps setup.
set -eu
stage=$1
mc=$2
if [ "$stage" = consumer ] || [ "$stage" = runtime ]; then
  python3 compile_legacy_probe.py --output results/phase6/legacy-binary
fi
case "$stage" in
 compile) python3 phase5_headless_check.py; python3 phase5_build.py --targets "$mc" --kind compile ;;
 publish) python3 phase5_build.py --targets "$mc" --kind publish ;;
 consumer)
   python3 ci/phase5/restore-local.py "$mc"
   python3 phase5_build.py --targets "$mc" --kind consumer
   python3 phase5_verify.py --targets "$mc" --stage artifacts
   python3 phase5_api.py --targets "$mc"
   ;;
 runtime)
   python3 ci/phase5/restore-local.py "$mc"
   python3 prepare_phase4_tls.py
   python3 phase5_run.py --targets "$mc" --kind network
   python3 phase5_verify.py --targets "$mc" --stage network
   xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' sh ci/phase5/x11-runtime.sh "$mc"
   python3 phase5_verify.py --targets "$mc" --stage runtime
   if [ "$mc" = 26.3 ]; then
     xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' sh ci/phase5/x11-runtime.sh "$mc" --modes rest --repeats 2
   fi
   ;;
 summary) python3 phase5_verify.py --stage summary ;;
 *) exit 2 ;;
esac
