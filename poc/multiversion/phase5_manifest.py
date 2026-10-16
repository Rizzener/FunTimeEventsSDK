"""Hash retained Phase 5 evidence and executable inputs after jobs have exited."""
import argparse,hashlib,json
from pathlib import Path
from run_phase2 import ROOT

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def main():
    args=argparse.ArgumentParser();args.add_argument('--verify',action='store_true');options=args.parse_args()
    destination=ROOT/'results/phase5/evidence-manifest.json'
    receipt=destination.with_name('manifest-verification.json')
    if options.verify:
        manifest=json.loads(destination.read_text());failed=[]
        for relative,record in manifest['files'].items():
            path=(ROOT/relative).resolve()
            if not path.is_relative_to(ROOT.resolve()) or not path.is_file() or path.stat().st_size!=record['bytes'] or sha(path)!=record['sha256']:failed.append(relative)
        result={'status':'FAIL' if failed else 'PASS','files_checked':len(manifest['files']),'mismatches':failed,'manifest_sha256':sha(destination)}
        receipt.write_text(json.dumps(result,indent=2)+'\n')
        print('Manifest verification',result['status'],'files',len(manifest['files']),'mismatches',failed)
        raise SystemExit(bool(failed))
    files={p for p in (ROOT/'results/phase5').rglob('*') if p.is_file() and p not in [destination,receipt]}
    for folder in ['core','platform','targets','consumers','fixtures','verification','gradle','ci']:
        files.update(p for p in (ROOT/folder).rglob('*') if p.is_file() and not
            any(x in p.relative_to(ROOT).parts for x in ['build','.gradle','run','__pycache__']))
    files.update(p for p in ROOT.iterdir() if p.is_file() and
        (p.suffix in ['.py','.md','.json','.kts','.properties'] or p.name in ['gradlew','gradlew.bat']))
    records={p.relative_to(ROOT).as_posix():{'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(files)}
    config=json.loads((ROOT/'targets.json').read_text())
    destination.write_text(json.dumps({'sdk_version':config['sdk_version'],'files':records,
        'scope':'Raw attempts including failures, collected local Linux evidence, immutable artifacts, source fixtures and reproduction commands. Excludes regenerable Gradle/world/cache outputs, this manifest and its verification receipt.',
        'accepted_phase4_manifest_sha256':sha(ROOT/'results/phase4/evidence-manifest.json')},indent=2)+'\n')
    print('Retained evidence/input files',len(records),'bytes',sum(r['bytes'] for r in records.values()))

if __name__=='__main__':main()
