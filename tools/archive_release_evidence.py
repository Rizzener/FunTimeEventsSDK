"""Durable local archive, independent of expiring GitHub Actions artifact storage."""
import argparse,hashlib,json,subprocess,zipfile,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'results/release-hardening'
def digest(p):
    with p.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def historical_performance():
    folder=ROOT/'release-evidence/2.0.0-rc.2/historical-performance';files={}
    phase5=ROOT/'poc/multiversion/results/phase5';phase6=ROOT/'results/phase6'
    inputs=set(phase5.rglob('performance*.csv'))|set(phase6.glob('performance*.csv'))|set(phase6.glob('performance*.json'))
    inputs.update(phase5.glob('**/runtime-*/attempt-*/result.json'))
    inputs.update(p for p in (phase6/'performance-controls').rglob('*') if p.is_file() and p.suffix in ['.json','.txt'])
    for p in sorted(inputs):
        relative=p.relative_to(ROOT);target=folder/relative;target.parent.mkdir(parents=True,exist_ok=True)
        if target.exists():assert digest(target)==digest(p),'Never overwrite historical archive'
        else:shutil.copy2(p,target)
        files[relative.as_posix()]={'bytes':target.stat().st_size,'sha256':digest(target)}
    assert files
    receipt={'status':'PASS','folder':folder.relative_to(ROOT).as_posix(),'files':files,'scope':'Historical Phase 5/6 measurements and failed matched-pair results, not RC2 matrix evidence.'}
    (folder/'manifest.json').write_text(json.dumps(receipt,indent=2)+'\n');(BASE/'historical-performance-archive.json').write_text(json.dumps({'status':'PASS','folder':receipt['folder'],'files':len(files),'manifest_sha256':digest(folder/'manifest.json')},indent=2)+'\n')
    print('HISTORICAL_PERFORMANCE_ARCHIVE',len(files))
def main():
    p=argparse.ArgumentParser();p.add_argument('--run-id',required=True);p.add_argument('--historical-performance',action='store_true');a=p.parse_args()
    if a.historical_performance:historical_performance();return
    verdict=json.loads((BASE/'verdict.json').read_text());assert str(verdict['run_id'])==a.run_id and verdict['status']=='PASS'
    version=verdict['version'];folder=ROOT/'release-evidence'/version;folder.mkdir(parents=True,exist_ok=True)
    source=folder/(verdict['sha']+'-source.zip')
    if not source.exists():subprocess.run(['git','archive','--format=zip','--output='+str(source),verdict['sha']],cwd=ROOT,check=True)
    inputs={source.name:source}
    for name in ['tools/hardening_report.py','tools/archive_release_evidence.py','tools/github_phase6.py']:
        inputs[name]=ROOT/name
    # Preserve every downloaded raw archive and workflow log, including canceled runs.
    for f in (BASE/'hosted').rglob('*'):
        if f.is_file() and (f.suffix=='.zip' or '.part' in f.name or 'collection-errors' in f.parts or f.suffix=='.json' and not any(part.startswith(('target-','aggregate-','phase6-summary-','loader-controls-')) for part in f.relative_to(BASE/'hosted').parts)):
            inputs[f.relative_to(ROOT).as_posix()]=f
    for sub in ['candidate','local','aggregate-api','license-negative-control','license-source-check']:
        for f in (BASE/sub).rglob('*'):
            if f.is_file():inputs[f.relative_to(ROOT).as_posix()]=f
    for f in BASE.glob('*'):
        if f.is_file() and f.name not in ['archive-receipt.json','evidence-manifest.json']:inputs[f.relative_to(ROOT).as_posix()]=f
    # Old crash diagnoses and baseline are retained as historical inputs, never RC2 PASS.
    for f in [ROOT/'poc/multiversion/NATIVE_CRASH_INVESTIGATION.md',ROOT/'NATIVE_CRASH_INVESTIGATION.md',ROOT/'PHASE6_RESULTS.md',ROOT/'results/phase6/performance-controls/result.json']:
        if f.is_file():inputs['historical/'+f.relative_to(ROOT).as_posix()]=f
    for name in json.loads((ROOT/'results/phase6/evidence-manifest.json').read_text())['files']:
        f=ROOT/name
        if f.suffix.lower()=='.wer' or f.name.startswith('hs_err_pid'):inputs['historical/'+name]=f
    native=ROOT/'poc/multiversion/results/phase3/native-investigation'
    for f in native.rglob('*'):
        if f.is_file() and f.suffix.lower() in ['.json','.txt','.wer','.log','.csv']:inputs['historical/'+f.relative_to(ROOT).as_posix()]=f
    dump=ROOT/'poc/multiversion/.evidence/native-dumps/no-sdk-20261015-144035/WER.86553cce-6297-4896-969d-8d660e63c696.tmp.dmp'
    if dump.exists():
        assert digest(dump)=='ca46417292770a1f1e085f0cf5554e3ded97b5bd387840cf037df57fcfa03175'
        inputs['historical/'+dump.relative_to(ROOT).as_posix()]=dump
    members={name:{'bytes':f.stat().st_size,'sha256':digest(f)} for name,f in sorted(inputs.items())}
    archive=folder/(a.run_id+'-attempt-'+str(verdict['attempt'])+'-critical.zip')
    # Archives are immutable. Changes need a separate archive name, never replacement.
    assert not archive.exists(),'Archive already exists; verify it rather than overwrite'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name,f in sorted(inputs.items()):z.write(f,name,compress_type=zipfile.ZIP_STORED if f.suffix=='.zip' else zipfile.ZIP_DEFLATED)
        z.writestr('ARCHIVE_MANIFEST.json',json.dumps({'version':version,'run_id':a.run_id,'sha':verdict['sha'],'files':members},indent=2)+'\n')
    with zipfile.ZipFile(archive) as z:
        bad=[n for n,v in members.items() if hashlib.sha256(z.read(n)).hexdigest()!=v['sha256']]
    assert not bad,bad
    result={'status':'PASS','archive':archive.relative_to(ROOT).as_posix(),'bytes':archive.stat().st_size,'sha256':digest(archive),'members':len(members),'verified_members':len(members),'source_archive':source.relative_to(ROOT).as_posix(),
            'storage':'Local Windows workspace, outside downloaded Actions artifact directories. No off-device backup performed.'}
    (folder/'archive-receipt.json').write_text(json.dumps(result,indent=2)+'\n');(BASE/'archive-receipt.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
