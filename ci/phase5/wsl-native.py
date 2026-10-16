"""Fresh Linux filesystem workspace/state; copy only hashed immutable cache inputs."""
import hashlib,json,os,shutil,time,sys,tarfile
from pathlib import Path

source=Path('/mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion')
task=Path('/var/tmp/fte-sdk-phase5-01a13db2')
workspace='native-b' if '--worker-b' in sys.argv else 'native'
repo=task/workspace/'repo/poc/multiversion';repo.mkdir(parents=True,exist_ok=True)
manifest={'source':{},'inputs':[],'started':time.time(),'status':'FAIL','workspace':workspace}
paths=set(p for p in source.iterdir() if p.is_file() and (p.suffix in ['.py','.md','.kts','.properties','.json'] or p.name in ['gradlew','gradlew.bat','.gitignore']))
for folder in ['core','platform','targets','consumers','fixtures','verification','gradle','ci']:
    for parent,dirs,files in os.walk(source/folder):
        dirs[:]=[d for d in dirs if d not in ['build','.gradle','run','__pycache__']]
        paths.update(Path(parent)/f for f in files)
for p in paths:
    dest=repo/p.relative_to(source);dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(p,dest);manifest['source'][p.relative_to(source).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
(repo/'gradlew').chmod(0o755)
if '--sources-only' in sys.argv:
    (source/('results/phase5/local-environment/wsl-'+workspace+'-source-update.json')).write_text(json.dumps({'source':manifest['source'],'updated':time.time()},indent=2)+'\n')
    print('Native source snapshot updated',len(paths),flush=True);raise SystemExit(0)
cache=task/workspace/'gradle';home=task/workspace/'home';cache.mkdir(exist_ok=True);home.mkdir(exist_ok=True)
original=task/'native/gradle' if workspace=='native-b' else source/'.evidence/phase5/wsl/gradle'
inputs=json.loads((source/'results/phase5/local-environment/wsl-inputs.json').read_text())['immutable_input_files']
if '--archive' in sys.argv:
    archive=source/'.evidence/phase5/wsl-native-inputs.tar'
    with tarfile.open(archive) as contents:
        for member in contents.getmembers():
            assert not Path(member.name).is_absolute() and '..' not in Path(member.name).parts
        contents.extractall(cache,filter='data')
for i,item in enumerate(inputs):
    relative=Path(item['path']);dest=cache/relative
    if str(relative).startswith('caches/fabric-loom/assets/'):
        continue
    p=original/relative
    if dest.exists():assert hashlib.sha256(dest.read_bytes()).hexdigest()==item['sha256'],dest
    else:
        dest.parent.mkdir(parents=True,exist_ok=True)
        if workspace=='native-b':os.link(p,dest)
        else:shutil.copy2(p,dest)
        assert hashlib.sha256(dest.read_bytes()).hexdigest()==item['sha256'],dest
    manifest['inputs'].append(item)
    # Do not keep the entire bulk seed resident on a 16 GiB shared PC.
    if workspace!='native-b':
        with dest.open('rb') as stream:
            os.fsync(stream.fileno())
            os.posix_fadvise(stream.fileno(),0,0,os.POSIX_FADV_DONTNEED)
    if i%1000==0:print('native copied input',i,'/',len(inputs),flush=True)
assets=cache/'caches/fabric-loom/assets'
if not assets.exists():assets.symlink_to(original/'caches/fabric-loom/assets',target_is_directory=True)
(cache/'gradle.properties').write_text('org.gradle.java.installations.paths='+str(task/'tools/usr/lib/jvm/java-21-openjdk-amd64')+','+str(task/'tools/usr/lib/jvm/java-25-openjdk-amd64')+'\norg.gradle.java.installations.auto-download=false\n')
manifest.update(status='PASS',seconds=time.time()-manifest['started'],fresh_home=str(home),gradle_user_home=str(cache),seed_from=str(original),
    note='Fresh native Linux source/home/Gradle state. Only hash-verified immutable dependency/raw Minecraft inputs copied or hard-linked. No candidate SDK or generated Loom Minecraft outputs seeded. Legacy SDK JAR/probe are intentional compatibility fixtures.')
(source/('results/phase5/local-environment/wsl-'+workspace+'-inputs.json')).write_text(json.dumps(manifest,indent=2)+'\n')
print('Native Linux workspace ready',len(paths),'sources',len(manifest['inputs']),'inputs',flush=True)
