"""Real production Fabric Loader rejection: wrong target and incompatible SDK versions."""
import argparse,json,os,subprocess,time,zipfile
from pathlib import Path
from run_phase2 import ROOT,gradle,terminate_owned,maven_local

def consumer(path,identifier,sdk,required,mc):
    metadata={'schemaVersion':1,'id':identifier,'version':'1.0.0','environment':'client',
              'depends':{'minecraft':'='+mc,'fte-api':'='+required},'jars':[{'file':'META-INF/jars/'+sdk.name}]}
    with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('fabric.mod.json',json.dumps(metadata));z.write(sdk,'META-INF/jars/'+sdk.name)

def main():
    p=argparse.ArgumentParser();p.add_argument('--target',required=True);p.add_argument('--wrong-target',required=True);a=p.parse_args()
    c=json.loads((ROOT/'targets.json').read_text());v=c['sdk_version'];group=c['maven_group'].replace('.','/')
    repo=ROOT/'build/staging-maven'/group
    sdk=repo/('mc'+a.target)/v/f'mc{a.target}-{v}.jar'
    wrong=repo/('mc'+a.wrong_target)/v/f'mc{a.wrong_target}-{v}.jar'
    assert sdk.is_file() and wrong.is_file()
    prefix=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/a.target/'loader-controls'
    out=prefix/('attempt-%02d'%(len(list(prefix.glob('attempt-*')))+1));out.mkdir(parents=True)
    # A second immutable SDK version from the same source, published only locally.
    probe=v+'-loader-probe'
    publication=gradle(':mc'+a.target+':publishMavenJavaPublicationToStagingRepository','-Ptarget_versions='+a.target,'-Pmod_version='+probe)
    probejar=repo/('mc'+a.target)/probe/f'mc{a.target}-{probe}.jar'
    if probejar.exists():raise RuntimeError('Refusing to reuse probe artifact from another attempt')
    with (out/'probe-publication.txt').open('wb') as log:
        result=subprocess.run(publication,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    assert result.returncode==0 and probejar.is_file()
    results={}
    for case in ['wrong-target','exact-version-conflict']:
        directory=out/case;directory.mkdir()
        first=directory/'first.jar';second=directory/'second.jar'
        consumer(first,'phase6-loader-first',wrong if case=='wrong-target' else sdk,v,a.target)
        mods=[first]
        if case=='exact-version-conflict':
            consumer(second,'phase6-loader-second',probejar,probe,a.target);mods.append(second)
        # ProductionRunTask receives only these consumers, not the regular test mod.
        # Multiple files use a directory whose JAR collection is resolved by Gradle.
        command=gradle('-p','consumers/mc'+a.target,'prodClient','-Pmod_version='+v,
            '-Pstaging_repository='+str(ROOT/'build/staging-maven'),'-Pphase6_mod_file='+str(directory))
        timeout=False
        with (directory/'client.txt').open('wb') as log:
            process=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
            try:process.wait(timeout=180)
            except subprocess.TimeoutExpired:timeout=True
            finally:terminate_owned(process)
        raw=(directory/'client.txt').read_text(encoding='utf-8',errors='replace')
        checks={'no_timeout':not timeout,'nonzero_exit':process.returncode!=0,
                'loader_diagnostic':'Incompatible mods found' in raw or 'Mod resolution encountered an incompatible mod set' in raw,
                'sdk_identified':'fte-api' in raw}
        checks['specific_reason']=(a.wrong_target in raw and a.target in raw) if case=='wrong-target' else (v in raw and probe in raw)
        results[case]={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'exit':process.returncode,'command':command}
    value={'status':'PASS' if all(r['status']=='PASS' for r in results.values()) else 'FAIL','cases':results,'probe_version':probe,'publication_command':publication}
    (out/'result.json').write_text(json.dumps(value,indent=2)+'\n');print(a.target,'LOADER_CONTROLS',value['status'],results)
    raise SystemExit(value['status']!='PASS')

if __name__=='__main__':main()
