"""Use a private Docker workspace on the connected Linux host; never touch its projects."""
import argparse,datetime,hashlib,json,os,shlex,subprocess,tarfile
from pathlib import Path
from run_phase2 import ROOT

HOST='bloodthrone'
REMOTE='/var/tmp/fte-sdk-phase5-01a13db2'
def execute(command,output):
    with output.open('wb') as log:r=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    return {'command':command,'exit':r.returncode,'log':str(output.relative_to(ROOT))}

def main():
    p=argparse.ArgumentParser();p.add_argument('--action',choices=['image','package','sync','run','collect'],required=True)
    p.add_argument('--kind',choices=['compile','publish','consumer']);p.add_argument('--targets',default='all');a=p.parse_args()
    if a.action!='package':raise SystemExit('Remote operations disabled: user authorized local Windows/WSL checks only. Historical public-image logs are retained.')
    ssh=['rtk','proxy','ssh','-o','BatchMode=yes','-o','ConnectTimeout=15',HOST]
    if a.action!='image':
        parent=ROOT/'results/phase5'/('linux-'+a.action);parent.mkdir(parents=True,exist_ok=True)
        output=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));output.mkdir()
        state_file=ROOT/'results/phase5/remote-state.json'
        commands=[]
        if a.action in ['sync','package']:
            paths=set(p for p in ROOT.iterdir() if p.is_file() and (p.suffix in ['.py','.md','.kts','.properties','.json'] or p.name in ['gradlew','gradlew.bat','.gitignore']))
            for folder in ['core','platform','targets','consumers','fixtures','verification','gradle','ci','.github']:
                for path in (ROOT/folder).rglob('*'):
                    if path.is_file() and not any(x in path.relative_to(ROOT).parts for x in ['build','.gradle','run','__pycache__']):paths.add(path)
            digest=hashlib.sha256()
            for path in sorted(paths):digest.update(path.relative_to(ROOT).as_posix().encode());digest.update(path.read_bytes())
            key=digest.hexdigest()[:16];archive=ROOT/'.evidence/phase5'/('source-'+key+'.tar.gz')
            with tarfile.open(archive,'w:gz') as tar:
                for path in sorted(paths):tar.add(path,arcname='poc/multiversion/'+path.relative_to(ROOT).as_posix())
            image_result=sorted((ROOT/'results/phase5/linux-preparation').glob('attempt-*/result.json'))[-1]
            image=json.loads(image_result.read_text())['image']
            remote_repo=REMOTE+'/snapshots/'+key+'/repo'
            if a.action=='package':
                result={'status':'PASS','archive':str(archive),'archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
                    'destination':HOST+':'+remote_repo,'files':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)},
                    'excluded':['.git','results','.evidence','build','.gradle','run','native dumps','private TLS keys']}
                (output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('Local package ready',len(paths),'files',str(archive),flush=True)
                raise SystemExit(0)
            commands.append(execute(ssh+['install -d '+shlex.quote(remote_repo)],output/'prepare.txt'))
            commands.append(execute(['rtk','proxy','scp','-o','BatchMode=yes',str(archive),HOST+':'+REMOTE+'/source-'+key+'.tar.gz'],output/'upload.txt'))
            commands.append(execute(ssh+['tar -xzf '+shlex.quote(REMOTE+'/source-'+key+'.tar.gz')+' -C '+shlex.quote(remote_repo)+'; chmod +x '+shlex.quote(remote_repo+'/poc/multiversion/gradlew')],output/'unpack.txt'))
            state={'snapshot':key,'repo':remote_repo,'image':image,'source_files':len(paths),'source_sha256':digest.hexdigest()}
            state_file.write_text(json.dumps(state,indent=2)+'\n');result=state.copy()
        else:
            state=json.loads(state_file.read_text());result=state.copy()
            if a.action=='run':
                if a.kind is None:raise SystemExit('--kind is required for run')
                config=json.loads((ROOT/'targets.json').read_text());known={t['minecraft'] for t in config['targets']}
                if a.targets!='all' and not set(a.targets.split(','))<=known:raise SystemExit('Unknown targets')
                name='fte-phase5-'+state['snapshot'][:8]+'-'+a.kind+'-'+datetime.datetime.now().strftime('%H%M%S')
                inner='set -eu; printf \'org.gradle.java.installations.paths=/opt/jdks/21,/opt/jdks/25\\norg.gradle.java.installations.auto-download=false\\n\' > /gradle-cache/gradle.properties; python -u -X utf8 phase5_build.py --kind '+a.kind+' --targets '+shlex.quote(a.targets)
                docker=['docker','run','--rm','--name',name,'--label','codex-task=fte-sdk-phase5-01a13db2','--cpus','3','--memory','6g','--pids-limit','2048',
                    '-v',state['repo']+':/repo','-v',REMOTE+'/gradle-cache:/gradle-cache','-v',REMOTE+'/m2:/root/.m2',
                    '-e','GRADLE_USER_HOME=/gradle-cache','-e','FTE_PHASE5_EVIDENCE=results/phase5/linux/'+state['snapshot'],state['image'],'sh','-lc',inner]
                commands.append(execute(ssh+[shlex.join(docker)],output/'docker.txt'))
                result['container']=name;result['kind']=a.kind;result['targets']=a.targets
            else:
                archive=REMOTE+'/results-'+state['snapshot']+'.tar.gz'
                commands.append(execute(ssh+['tar -czf '+shlex.quote(archive)+' -C '+shlex.quote(state['repo']+'/poc/multiversion')+' results'],output/'pack.txt'))
                local=ROOT/'.evidence/phase5'/('results-'+state['snapshot']+'.tar.gz')
                commands.append(execute(['rtk','proxy','scp','-o','BatchMode=yes',HOST+':'+archive,str(local)],output/'download.txt'))
                if commands[-1]['exit']==0:
                    with tarfile.open(local) as tar:tar.extractall(ROOT,filter='data')
        result.update({'commands':commands,'status':'PASS' if all(c['exit']==0 for c in commands) else 'FAIL'})
        (output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(a.action,result['status'],result['snapshot'],flush=True)
        raise SystemExit(result['status']!='PASS')
    parent=ROOT/'results/phase5/linux-preparation';parent.mkdir(parents=True,exist_ok=True)
    output=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));output.mkdir()
    context=ROOT/'.evidence/phase5/ci-image.tar.gz';context.parent.mkdir(parents=True,exist_ok=True)
    with tarfile.open(context,'w:gz') as tar:
        for name in ['Dockerfile','jdks.json','install_jdks.py']:tar.add(ROOT/'ci/phase5'/name,arcname=name)
    key=hashlib.sha256(context.read_bytes()).hexdigest()[:12];image='fte-sdk-phase5:'+key
    commands=[]
    commands.append(execute(ssh+['install -d '+shlex.quote(REMOTE+'/image')],output/'prepare.txt'))
    commands.append(execute(['rtk','proxy','scp','-o','BatchMode=yes',str(context),HOST+':'+REMOTE+'/ci-image.tar.gz'],output/'upload.txt'))
    script='set -eu; cd '+shlex.quote(REMOTE)+'; tar -xzf ci-image.tar.gz -C image; docker pull ubuntu:24.04; BASE=$(docker image inspect ubuntu:24.04 --format \'{{index .RepoDigests 0}}\'); printf "%s\\n" "$BASE" > base-image.txt; docker build --build-arg BASE="$BASE" -t '+shlex.quote(image)+' image; docker image inspect '+shlex.quote(image)+' --format \'{{.Id}}\'; cat base-image.txt'
    commands.append(execute(ssh+[script],output/'image-build.txt'))
    result={'status':'PASS' if all(c['exit']==0 for c in commands) else 'FAIL','host':HOST,'private_root':REMOTE,'image':image,'context_sha256':hashlib.sha256(context.read_bytes()).hexdigest(),'commands':commands}
    (output/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(result['status'],image,flush=True)
    raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
