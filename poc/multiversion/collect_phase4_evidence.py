"""Retain exact published experimental artifacts, source guards and all attempts."""
import hashlib, json, shutil
from pathlib import Path
from run_phase2 import ROOT

def main():
    output=ROOT/'results/phase4/artifacts';output.mkdir(parents=True,exist_ok=True)
    manifest={'artifacts':{},'attempts':[]}
    legacy=ROOT.parents[1]/'fte-api/build/libs/fte-api-1.0.5-SNAPSHOT.jar'
    retained=ROOT/'fixtures/binary-compat/legacy-sdk.jar'
    if not retained.exists():shutil.copy2(legacy,retained)
    manifest['legacy_baseline']={'path':str(retained.relative_to(ROOT)),'sha256':hashlib.sha256(retained.read_bytes()).hexdigest(),'bytes':retained.stat().st_size}
    for mc in ['1.21.4','26.3']:
        repo=Path.home()/'.m2/repository/net/funtimeevents/poc'/('mc'+mc)
        for version in repo.glob('*phase4*'):
            if not version.is_dir():continue
            dest=output/version.name/('mc'+mc);dest.mkdir(parents=True,exist_ok=True)
            for source in version.iterdir():
                if source.suffix not in ['.jar','.pom','.module']:continue
                target=dest/source.name;shutil.copy2(source,target)
                manifest['artifacts'][str(target.relative_to(ROOT))]={'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
    for path in sorted((ROOT/'results/phase4').rglob('result.json')):
        if not path.parent.name.startswith('attempt-'):continue
        result=json.loads(path.read_text(encoding='utf-8'))
        manifest['attempts'].append({'path':str(path.relative_to(ROOT)),'status':result.get('status'),'error':result.get('error')})
    (ROOT/'results/phase4/evidence-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Retained',len(manifest['artifacts']),'Maven files;',len(manifest['attempts']),'attempt records')
if __name__=='__main__':main()
