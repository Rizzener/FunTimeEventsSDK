"""Recheck collected Linux production evidence with the current strict verifier."""
from pathlib import Path
import phase5_verify as verify
from phase5_linux_results import PREFIXES

def main():
    rows=[]
    for prefix in PREFIXES:
        verify.PREFIX=Path(prefix)
        for spec in verify.CONFIG['targets']:
            mc=spec['minecraft'];base=verify.ROOT/prefix/mc
            if not any(list((base/('runtime-'+mode)).glob('attempt-*/result.json')) for mode in ['offline','rest','ws','flags']):continue
            result=verify.runtime(mc);rows.append(result['status'])
            print(prefix,mc,result['status'],flush=True)
    raise SystemExit(any(r!='PASS' for r in rows))

if __name__=='__main__':main()
