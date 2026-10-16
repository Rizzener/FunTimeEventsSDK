"""Compile the exact documented standalone Maven consumer with no Maven Local."""
import argparse,json,os,subprocess,shutil,zipfile
from run_phase2 import ROOT,gradle
def main():
    p=argparse.ArgumentParser();p.add_argument('--target',required=True);a=p.parse_args()
    parent=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/a.target/'doc-examples';parent.mkdir(parents=True,exist_ok=True)
    if (parent/'gradle.txt').exists():
        historical=parent/'initial-build';historical.mkdir(exist_ok=True)
        for name in ['gradle.txt','result.json']:
            if (parent/name).exists():shutil.move(parent/name,historical/name)
    out=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));out.mkdir()
    code=(ROOT/'examples/consumer/src/main/java/example/ExampleClient.java').read_text().rstrip()
    c=json.loads((ROOT/'targets.json').read_text());coordinate=c['maven_group']+':mc'+a.target+':'+c['sdk_version']
    checks={'exact_Java_in_README':code in (ROOT/'README.md').read_text(encoding='utf-8'),
            'target_coordinate_in_DOC':coordinate in (ROOT/'DOC.md').read_text(encoding='utf-8'),
            'no_Maven_Local': 'mavenLocal' not in (ROOT/'examples/consumer/build.gradle').read_text(),
            'standalone_catalog_matches_registry':json.loads((ROOT/'examples/consumer/targets.json').read_text())==c}
    project=ROOT/'.evidence/phase6/standalone-consumers'/a.target/out.name
    project.mkdir(parents=True)
    for source in (ROOT/'examples/consumer').rglob('*'):
        relative=source.relative_to(ROOT/'examples/consumer')
        if relative.parts[0] in ['build','.gradle'] or not source.is_file():continue
        target=project/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
    with zipfile.ZipFile(out/'standalone-source.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for source in project.rglob('*'):
            if source.is_file():archive.write(source,source.relative_to(project))
    # Use the copied project's own wrapper, without the SDK root or its build registry.
    def standalone_command(repository):
        command=gradle('-p',str(project),'build','-Pminecraft_version='+a.target,'-Psdk_repository='+str(repository))
        name='gradlew.bat' if os.name=='nt' else 'gradlew'
        converted=[]
        for value in command:
            if value==str(ROOT/name):converted+=([str(project/name)] if os.name=='nt' else ['sh',str(project/name)])
            else:converted.append(value)
        return converted
    command=standalone_command(ROOT/'build/staging-maven')
    with (out/'gradle.txt').open('wb') as log:r=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    checks['independent_build']=r.returncode==0
    for artifact in (project/'build/libs').glob('*.jar'):
        target=out/'artifacts'/artifact.name;target.parent.mkdir(exist_ok=True);shutil.copy2(artifact,target)
    negative=standalone_command(out/'nonexistent-maven')
    with (out/'missing-repository.txt').open('wb') as log:missing=subprocess.run(negative,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    raw=(out/'missing-repository.txt').read_text(encoding='utf-8',errors='replace')
    checks['missing_staging_fails_without_Maven_Local_fallback']=missing.returncode!=0 and ('mc'+a.target) in raw and 'nonexistent-maven' in raw
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'command':command,'exit':r.returncode,'negative_command':negative,'negative_exit':missing.returncode}
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n');shutil.copy2(out/'result.json',parent/'result.json');print(a.target,'DOC_EXAMPLES',result['status'])
    raise SystemExit(result['status']!='PASS')
if __name__=='__main__':main()
