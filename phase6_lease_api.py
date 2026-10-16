"""Additive ownership API descriptors; old signatures are checked separately."""
import argparse,json,os
from run_phase2 import ROOT,maven_local
from verify_phase4_compat import signatures
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--target',required=True);a=p.parse_args()
    c=json.loads((ROOT/'targets.json').read_text());v=c['sdk_version']
    jar=maven_local()/c['maven_group'].replace('.','/')/('mc'+a.target)/v/('mc'+a.target+'-'+v+'.jar')
    builder='net.funtimeevents.api.FteConfig$Builder';lease='net.funtimeevents.api.RuntimeLease'
    sig,raw=signatures(jar,[builder,lease])
    checks={'build_unchanged':'build ()Lnet/funtimeevents/api/FunTimeEventsAPI;' in sig[builder],
            'buildLease':'buildLease ()Lnet/funtimeevents/api/RuntimeLease;' in sig[builder],
            'close':'close ()V' in sig[lease],
            'AutoCloseable':'RuntimeLease implements java.lang.AutoCloseable' in raw,
            'no_public_lease_constructor':all(not s.startswith('RuntimeLease ') for s in sig[lease])}
    out=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/release-hardening/local')/a.target/'lease-api';out.mkdir(parents=True,exist_ok=True)
    (out/'javap.txt').write_text(raw);(out/'result.json').write_text(json.dumps({'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks},indent=2)+'\n')
    print('LEASE_API',a.target,checks);raise SystemExit(not all(checks.values()))
