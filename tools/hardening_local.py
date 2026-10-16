"""Run an existing check with fresh rc.2 evidence and an isolated Maven Local."""
import os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
if __name__=='__main__':
    env=dict(os.environ)
    env.setdefault('FTE_PHASE5_EVIDENCE','results/release-hardening/local')
    env.setdefault('FTE_HOSTED_EVIDENCE','results/release-hardening/hosted')
    env.setdefault('FTE_MAVEN_LOCAL',str(ROOT/'.evidence/release-hardening/maven-local'))
    raise SystemExit(subprocess.call([sys.executable,*sys.argv[1:]],cwd=ROOT,env=env))
