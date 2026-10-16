"""Remove only this task's finished example Gradle cache, preserving all evidence."""
import argparse,datetime,hashlib,json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--historical-fixture-inputs',action='store_true');args=parser.parse_args()
    if args.historical_fixture_inputs:
        removed=[];out=ROOT/'results/phase6/local-environment/historical-regenerable-inputs'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S');out.mkdir(parents=True)
        for relative in ['poc/multiversion/targets/mc26.3/build/phase3-server','poc/multiversion/consumers/mc26.3/build/phase3-client']:
            directory=(ROOT/relative).resolve();assert directory.is_relative_to(ROOT/'poc/multiversion') and directory.name in ['phase3-server','phase3-client'] and directory.parent.name=='build'
            for jar in directory.rglob('*.jar'):
                if jar.stat().st_size<500_000:continue
                removed.append({'path':jar.relative_to(ROOT).as_posix(),'bytes':jar.stat().st_size,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()});jar.unlink()
            for world in directory.glob('phase3-world-*'):
                assert world.resolve().parent==directory and not world.is_junction() and not world.is_symlink()
                for f in world.rglob('*'):
                    if f.is_file():removed.append({'path':f.relative_to(ROOT).as_posix(),'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()})
                shutil.rmtree('\\\\?\\'+str(world))
        (out/'inventory.json').write_text(json.dumps(removed,indent=2)+'\n')
        print({'scope':'Only regenerable game libraries/worlds of the completed historical benchmark, never sources, fixture mod JARs, results or dumps','bytes':sum(f['bytes'] for f in removed),'free':shutil.disk_usage(ROOT).free});return
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
