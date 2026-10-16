"""Freeze owned sources/fixtures/build tools, excluding caches, game binaries and TLS keys."""
import hashlib, json, zipfile
from run_phase2 import ROOT

def main():
    paths=set(p for p in ROOT.iterdir() if p.is_file() and p.suffix in ['.py','.md','.kts','.properties'])
    paths.update(ROOT/p for p in ['gradlew','gradlew.bat','.gitignore'])
    for directory in ['core','platform','targets','consumers','fixtures','verification','gradle']:
        for p in (ROOT/directory).rglob('*'):
            if not p.is_file():continue
            parts=p.relative_to(ROOT).parts
            if any(x in parts for x in ['build','.gradle','run','__pycache__']):continue
            paths.add(p)
    output=ROOT/'results/phase4/4C/candidate-source.zip'
    manifest={}
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(paths):
            name=path.relative_to(ROOT).as_posix()
            archive.write(path,name)
            manifest[name]=hashlib.sha256(path.read_bytes()).hexdigest()
    result={'path':str(output.relative_to(ROOT)),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
            'files':manifest,'excluded':['results','.evidence','build','.gradle','run','__pycache__']}
    output.with_suffix('.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('Archived',len(paths),'source/build/fixture files',result['sha256'])

if __name__=='__main__':main()
