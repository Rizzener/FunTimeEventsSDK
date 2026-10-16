"""Read-only verification that accepted Phase 5 inputs and evidence survived promotion."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def main():
    old=ROOT/'poc/multiversion';manifest=json.loads((old/'results/phase5/evidence-manifest.json').read_text())
    mismatches=[]
    for relative,expected in manifest['files'].items():
        p=old/relative
        if not p.exists() or p.stat().st_size!=expected['bytes'] or hashlib.sha256(p.read_bytes()).hexdigest()!=expected['sha256']:
            mismatches.append(relative)
    value={'status':'PASS' if not mismatches else 'FAIL','files':len(manifest['files']),'mismatches':mismatches}
    out=ROOT/'results/phase6/historical-integrity.json';out.write_text(json.dumps(value,indent=2)+'\n');print(value)
    raise SystemExit(bool(mismatches))
if __name__=='__main__':main()
