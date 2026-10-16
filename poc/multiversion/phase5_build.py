"""Sequential per-target stages with immutable publication and retained failed attempts."""
import argparse,datetime,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
from run_phase2 import ROOT,gradle,maven_local

def run(mc,kind,version):
    config=json.loads((ROOT/'targets.json').read_text());spec=next(t for t in config['targets'] if t['minecraft']==mc)
    legacy=spec['java']==21
    parent=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase5')/mc/kind;parent.mkdir(parents=True,exist_ok=True)
    output=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));output.mkdir()
    base=['-Ptarget_versions='+mc,'-Ppoc_version='+version]
    if kind=='compile':args=[':core:check',':mc'+mc+':classes',':mc'+mc+':serverFixtureClasses',':mc'+mc+':networkVerificationClasses']+base
    elif kind=='publish':
        jar=maven_local()/'net/funtimeevents/poc'/('mc'+mc)/version/('mc'+mc+'-'+version+'.jar')
        if jar.exists():raise SystemExit('Refusing to overwrite published '+str(jar))
        args=[':core:check',':mc'+mc+':build',':mc'+mc+':publishToMavenLocal',':mc'+mc+':publishMavenJavaPublicationToPhase5Repository',
              ':mc'+mc+(':'+('remapServerFixtureJar' if legacy else 'serverFixtureJar'))]+base
    else:args=['-p','consumers/mc'+mc,'build','remapPeerJar' if legacy else 'peerJar','downloadAssets','-Ppoc_version='+version,
               '-Pphase5_repository='+str(ROOT/'build/phase5-maven')]
    command=gradle(*args)
    result={'target':mc,'kind':kind,'version':version,'started':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':command,'status':'FAIL'}
    with (output/'gradle.txt').open('wb') as log:
        process=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    result['exit']=process.returncode;result['status']='PASS' if process.returncode==0 else 'FAIL'
    if kind=='publish' and result['status']=='PASS':
        result['artifacts']={}
        source=maven_local()/'net/funtimeevents/poc'/('mc'+mc)/version
        dest=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase5')/'artifacts'/version/('mc'+mc);dest.mkdir(parents=True,exist_ok=True)
        for path in source.iterdir():
            if path.suffix not in ['.jar','.pom','.module']:continue
            target=dest/path.name;shutil.copy2(path,target)
            result['artifacts'][str(target.relative_to(ROOT))]=hashlib.sha256(target.read_bytes()).hexdigest()
    (output/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(mc,kind,result['status'],output.relative_to(ROOT),flush=True)
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);p.add_argument('--kind',choices=['compile','publish','consumer'],required=True);p.add_argument('--version')
    a=p.parse_args();config=json.loads((ROOT/'targets.json').read_text());version=a.version or config['sdk_version']
    targets=[t['minecraft'] for t in config['targets']] if a.targets=='all' else a.targets.split(',')
    results=[run(mc,a.kind,version) for mc in targets]
    raise SystemExit(any(r['status']!='PASS' for r in results))
