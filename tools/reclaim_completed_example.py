"""Remove only this task's finished example Gradle cache, preserving all evidence."""
import datetime,hashlib,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    target=(ROOT/'examples/consumer/.gradle').resolve()
    assert target.is_relative_to(ROOT) and target.parent==ROOT/'examples/consumer' and target.name=='.gradle'
    assert not target.is_symlink() and not target.is_junction()
    out=ROOT/'results/phase6/local-environment/completed-example-cache'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S');out.mkdir(parents=True)
    files=[]
    if target.exists():
        for p in target.rglob('*'):
            assert not p.is_junction()
            if p.is_file():
                files.append({'path':p.relative_to(target).as_posix(),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
                if p.suffix=='.log':
                    copy=out/'cache-logs'/p.relative_to(target);copy.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,copy)
        (out/'inventory.json').write_text(json.dumps(files,indent=2)+'\n')
        shutil.rmtree('\\\\?\\'+str(target))
    result={'target':str(target),'bytes':sum(p['bytes'] for p in files),'free_after':shutil.disk_usage(ROOT).free,'scope':'Only finished standalone example .gradle. Build JARs, Maven artifacts, fixtures and raw test evidence preserved.'}
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(result)
if __name__=='__main__':main()
