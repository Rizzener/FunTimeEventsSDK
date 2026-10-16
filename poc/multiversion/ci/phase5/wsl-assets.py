"""Local immutable assets transfer to native Linux FS; never touches SDK outputs."""
import argparse,hashlib,json,os,re,tarfile,time
from pathlib import Path

source=Path(__file__).resolve().parents[2]
archive=source/'.evidence/phase5/wsl-assets.tar'
manifest=source/'results/phase5/local-environment/wsl-assets.json'
task=Path('/var/tmp/fte-sdk-phase5-01a13db2/native')
prepared=task/'assets-prepared'
assets=task/'gradle/caches/fabric-loom/assets'

def digest(path,algorithm='sha256'):
    h=hashlib.new(algorithm)
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def package():
    original=source/'.evidence/phase5/wsl/gradle/caches/fabric-loom/assets'
    rows=[];archive.parent.mkdir(parents=True,exist_ok=True)
    known={}
    for spec in json.loads((source/'targets.json').read_text())['targets']:
        info=json.loads((Path.home()/'.gradle/caches/fabric-loom'/spec['minecraft']/'mojang_minecraft_info.json').read_text())['assetIndex']
        known[str(info['id'])]=info['sha1'];known[spec['minecraft']+'-'+str(info['id'])]=info['sha1']
    with tarfile.open(archive,'w') as tar:
        for path in sorted(original.rglob('*')):
            if not path.is_file():continue
            relative=path.relative_to(original)
            if relative.parts[0]=='objects' and re.fullmatch('[0-9a-f]{40}',path.name):
                assert digest(path,'sha1')==path.name,path
            elif relative.parts[0]=='indexes' and path.stem in known:
                assert digest(path,'sha1')==known[path.stem],path
            else:continue
            tar.add(path,arcname=relative.as_posix(),recursive=False)
            rows.append({'path':relative.as_posix(),'bytes':path.stat().st_size,'sha256':digest(path)})
    manifest.write_text(json.dumps({'status':'PACKAGED','files':rows,'archive_bytes':archive.stat().st_size,'archive_sha256':digest(archive)},indent=2)+'\n')
    print('Packaged verified official assets',len(rows),archive.stat().st_size,flush=True)

def prepare():
    d=json.loads(manifest.read_text())
    prepared.mkdir(parents=True,exist_ok=True)
    if not all((prepared/r['path']).exists() and (prepared/r['path']).stat().st_size==r['bytes'] for r in d['files']):
        assert digest(archive)==d['archive_sha256']
        with tarfile.open(archive) as tar:
            assert all(not Path(m.name).is_absolute() and '..' not in Path(m.name).parts for m in tar.getmembers())
            tar.extractall(prepared,filter='data')
    for row in d['files']:
        p=prepared/row['path'];assert digest(p)==row['sha256'],p
        with p.open('rb') as f:
            os.fsync(f.fileno());os.posix_fadvise(f.fileno(),0,0,os.POSIX_FADV_DONTNEED)
    # An in-flight packaging run may predate the Loom-prefixed index name fix.
    # Add only indexes validated against the genuine per-version MC metadata.
    import shutil
    extras=[];rejected=[]
    for spec in json.loads((source/'targets.json').read_text())['targets']:
        info=json.loads((task/'gradle/caches/fabric-loom'/spec['minecraft']/'mojang_minecraft_info.json').read_text())['assetIndex']
        for name in [str(info['id'])+'.json',spec['minecraft']+'-'+str(info['id'])+'.json']:
            p=assets/'indexes'/name
            if not p.exists():continue
            actual=digest(p,'sha1')
            if actual!=info['sha1']:
                rejected.append({'path':str(p),'expected_sha1':info['sha1'],'actual_sha1':actual});continue
            dest=prepared/'indexes'/name;dest.parent.mkdir(exist_ok=True);shutil.copy2(p,dest)
            extras.append({'path':'indexes/'+name,'sha256':digest(dest)})
    d['validated_extra_indexes']=extras
    d['rejected_indexes_not_copied']=rejected
    d.update(status='PREPARED',prepared_at=time.time());manifest.write_text(json.dumps(d,indent=2)+'\n')
    print('Native Linux assets verified',len(d['files']),flush=True)

def activate():
    deadline=time.monotonic()+2700
    while True:
        busy=[]
        for p in Path('/proc').glob('[0-9]*/cmdline'):
            try:cmd=p.read_bytes().replace(b'\0',b' ').decode(errors='replace')
            except (FileNotFoundError,PermissionError,ProcessLookupError):continue
            if str(task) in cmd and ('prodClient' in cmd or 'KnotClient' in cmd):busy.append(int(p.parent.name))
        if not busy:break
        if time.monotonic()>deadline:raise TimeoutError('Assets activation needs an idle production-client interval')
        time.sleep(1)
    assert assets.is_symlink() and prepared.is_dir()
    backup=assets.with_name('assets-windows-link');assert not backup.exists()
    assets.rename(backup)
    try:prepared.rename(assets)
    except BaseException:backup.rename(assets);raise
    d=json.loads(manifest.read_text());d.update(status='ACTIVE',activated_at=time.time(),note='No owned prodClient wrapper/game process active during switch; Windows input link retained separately.')
    manifest.write_text(json.dumps(d,indent=2)+'\n');print('Native assets active',assets,flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--action',choices=['package','prepare','activate'],required=True)
    globals()[parser.parse_args().action]()
