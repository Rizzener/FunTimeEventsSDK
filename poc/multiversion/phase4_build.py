"""Sequential stage build/publication with immutable source/artifact/log evidence."""
import argparse, datetime, hashlib, json, subprocess, zipfile
from pathlib import Path
from run_phase2 import ROOT, gradle

def build(stage,override=None,consumers_only=False):
    version=override or {'a':'0.0.7-phase4a.2','b':'0.0.8-phase4b','c':'0.0.9-phase4c'}[stage]
    for mc in ['1.21.4','26.3']:
        existing=Path.home()/'.m2/repository/net/funtimeevents/poc'/('mc'+mc)/version/('mc'+mc+'-'+version+'.jar')
        if existing.exists() and not consumers_only:raise SystemExit('Refusing to overwrite published experimental version '+version+'; use frozen artifacts or --version with a new value')
    parent=ROOT/'results/phase4'/('4'+stage.upper())/('consumer-build' if consumers_only else 'build')
    parent.mkdir(parents=True,exist_ok=True)
    attempt=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));attempt.mkdir()
    results=[]
    commands=[] if consumers_only else [gradle('pocBuild','pocPublishLocal',':mc1.21.4:remapServerFixtureJar',':mc26.3:serverFixtureJar','-Ppoc_version='+version)]
    commands += [gradle('-p','consumers/mc'+mc,'build','-Ppoc_version='+version) for mc in ['1.21.4','26.3']]
    for i,command in enumerate(commands):
        print('BUILD',stage,i,flush=True)
        with (attempt/('command-%d.txt'%i)).open('wb') as log:
            p=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        results.append({'command':command,'exit':p.returncode})
        if p.returncode: break
    result={'version':version,'consumers_only':consumers_only,'commands':results,'status':'PASS' if len(results)==len(commands) and all(r['exit']==0 for r in results) else 'FAIL'}
    if result['status']=='PASS':
        with zipfile.ZipFile(attempt/'source.zip','w',zipfile.ZIP_DEFLATED) as archive:
            for pattern in ['core/src/**/*.java','platform/**/*.java','consumers/common/**/*.java','fixtures/server/**/*.java','**/*.gradle','**/*.gradle.kts']:
                for path in ROOT.glob(pattern):
                    if 'build' not in path.relative_to(ROOT).parts and '.gradle' not in path.relative_to(ROOT).parts:
                        archive.write(path,path.relative_to(ROOT))
        for mc in ['1.21.4','26.3']:
            jar=Path.home()/'.m2/repository/net/funtimeevents/poc'/('mc'+mc)/version/('mc'+mc+'-'+version+'.jar')
            result[mc]={'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'bytes':jar.stat().st_size}
    (attempt/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(attempt,result['status'],flush=True)
    return result
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--stage',choices=['a','b','c'],required=True);p.add_argument('--version')
    p.add_argument('--consumers-only',action='store_true',help='Build both consumers from frozen Maven JARs without republishing the SDK')
    args=p.parse_args();raise SystemExit(build(args.stage,args.version,args.consumers_only)['status']!='PASS')
