#!/bin/sh
# Every target runs from a clean hosted checkout, in a run/attempt-specific repository.
set -eu
mc=$1
python3 phase6_process_controls.py
python3 phase6_source_parity.py
python3 phase6_checker_controls.py
python3 phase5_headless_check.py
python3 compile_legacy_probe.py --output "$FTE_PHASE5_EVIDENCE/legacy-binary-$mc"
python3 phase5_build.py --targets "$mc" --kind compile
python3 phase5_build.py --targets "$mc" --kind publish
python3 phase5_build.py --targets "$mc" --kind consumer
python3 phase6_examples.py --target "$mc"
python3 phase5_verify.py --targets "$mc" --stage artifacts
python3 phase5_api.py --targets "$mc"
python3 phase6_compat_inventory.py --targets "$mc"
python3 prepare_phase4_tls.py
python3 phase5_run.py --targets "$mc" --kind network
python3 phase5_verify.py --targets "$mc" --stage network
if [ "$mc" = 26.3 ]; then
  xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' python3 ci/phase6/graphics_check.py
fi
xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' sh ci/phase5/x11-runtime.sh "$mc"
if [ "$mc" = 26.3 ]; then
  xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' sh ci/phase5/x11-runtime.sh "$mc" --modes rest --repeats 2
fi
python3 phase5_verify.py --targets "$mc" --stage runtime
python3 phase6_artifacts.py --targets "$mc"
python3 phase6_perf.py --targets "$mc"
python3 phase6_provenance.py --target "$mc"
