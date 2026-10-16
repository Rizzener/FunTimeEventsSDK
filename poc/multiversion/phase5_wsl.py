"""Local WSL only: fresh source/home and Gradle state, hashed immutable inputs.

Dependency artifacts/assets are hard-linked on C: to fit the local disk. No SDK
build outputs, Gradle metadata, locks, or Windows-generated Minecraft outputs
are seeded. The original dependency files are never edited by this tool.
"""
import argparse,hashlib,json,os,shutil,subprocess
from pathlib import Path
from run_phase2 import ROOT

WORK=ROOT/'.evidence/phase5/wsl'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def prepare(sources_only=False):
    paths=set(p for p in ROOT.iterdir() if p.is_file() and (p.suffix in ['.py','.md','.kts','.properties','.json'] or p.name in ['gradlew','gradlew.bat','.gitignore']))
    for folder in ['core','platform','targets','consumers','fixtures','verification','gradle','ci']:
        for path in (ROOT/folder).rglob('*'):
            if path.is_file() and not any(x in path.relative_to(ROOT).parts for x in ['build','.gradle','run','__pycache__']):paths.add(path)
    repo=WORK/'repo/poc/multiversion';repo.mkdir(parents=True,exist_ok=True)
    sources={}
    for p in sorted(paths):
        dest=repo/p.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.exists() and sha(dest)==sha(p):continue
        shutil.copy2(p,dest);sources[p.relative_to(ROOT).as_posix()]=sha(p)
    if sources_only:
        (ROOT/'results/phase5/local-environment/wsl-source-update.json').write_text(json.dumps({'updated_sources':sources,'source_root':str(repo)},indent=2)+'\n')
        print('Updated local WSL source files',len(sources),flush=True);return
    cache=WORK/'gradle';home=WORK/'home';cache.mkdir(exist_ok=True);home.mkdir(exist_ok=True)
    origin=Path.home()/'.gradle';inputs=[]
    selected=list((origin/'caches/modules-2/files-2.1').rglob('*'))+list((origin/'wrapper/dists/gradle-9.6.1-bin').rglob('*'))+list((origin/'caches/fabric-loom/assets').rglob('*'))
    for mc in json.loads((ROOT/'targets.json').read_text())['targets']:
        folder=origin/'caches/fabric-loom'/mc['minecraft']
        selected += [p for p in folder.iterdir() if p.name in ['minecraft-client.jar','minecraft-server.jar','mojang_minecraft_info.json']]
    for p in selected:
        if not p.is_file() or p.suffix in ['.lock','.lck','.part']:continue
        relative=p.relative_to(origin);dest=cache/relative;dest.parent.mkdir(parents=True,exist_ok=True)
        if not dest.exists():os.link('\\\\?\\'+str(p.resolve()),'\\\\?\\'+str(dest.resolve()))
        inputs.append({'path':relative.as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)})
    (cache/'gradle.properties').write_text('org.gradle.java.installations.paths=/var/tmp/fte-sdk-phase5-01a13db2/tools/usr/lib/jvm/java-21-openjdk-amd64,/var/tmp/fte-sdk-phase5-01a13db2/tools/usr/lib/jvm/java-25-openjdk-amd64\norg.gradle.java.installations.auto-download=false\n')
    out=ROOT/'results/phase5/local-environment/wsl-inputs.json';out.write_text(json.dumps({'sources':sources,'immutable_input_files':inputs,'source_root':str(repo),'fresh_home':str(home),'note':'No candidate SDK classes/JARs or mutable Gradle state seeded'},indent=2)+'\n')
    print('WSL local source',len(paths),'immutable inputs',len(inputs),flush=True)

def run(kind,targets,modes,label='matrix',workspace='native'):
    # This shell is task-owned and invokes only local Linux processes.
    script=ROOT/'ci/phase5/wsl-run.sh'
    out=ROOT/'results/phase5/local-environment/wsl-jobs'/kind;out.mkdir(parents=True,exist_ok=True)
    number=len(list(out.glob('attempt-*.txt')))+1
    with (out/('attempt-%02d.txt'%number)).open('wb') as log:
        p=subprocess.run(['rtk','proxy','wsl','-d','Ubuntu','-u','root','--','python3','/mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-limit.py','/mnt/c/dev/github/rizzener/FunTimeEventsSDK/poc/multiversion/ci/phase5/wsl-run.sh',kind,targets,modes,label,workspace],stdout=log,stderr=subprocess.STDOUT)
    # Preserve Linux raw logs outside the ignored scratch area.
    source=WORK/'repo/poc/multiversion/results/phase5/wsl'
    if source.exists():shutil.copytree(source,ROOT/'results/phase5/wsl',dirs_exist_ok=True)
    print('WSL',kind,'exit',p.returncode,flush=True);return p.returncode

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--action',choices=['prepare','run'],required=True);p.add_argument('--kind',default='compile');p.add_argument('--targets',default='1.21.5');p.add_argument('--modes',default='offline,rest,ws,flags');p.add_argument('--label',default='matrix');p.add_argument('--workspace',choices=['native','native-b'],default='native');p.add_argument('--sources-only',action='store_true');a=p.parse_args()
    if a.action=='prepare':prepare(a.sources_only)
    else:raise SystemExit(run(a.kind,a.targets,a.modes,a.label,a.workspace))
