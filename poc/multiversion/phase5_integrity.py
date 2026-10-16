"""Check frozen SDK sources, accepted Phase 4 artifacts, and release isolation."""
import hashlib,json,subprocess,shutil
from run_phase2 import ROOT,maven_local

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def local(path):return ROOT/path.replace('\\','/')

def main():
    frozen=json.loads((ROOT/'results/phase5/sdk-source-freeze.json').read_text());checks={};failures=[]
    checks['frozen_76_SDK_sources']=len(frozen)==76 and all(local(p).exists() and sha(local(p))==value for p,value in frozen.items())
    actual={p.relative_to(ROOT).as_posix() for prefix in ['core/src/main/java','platform'] for p in (ROOT/prefix).rglob('*.java')}
    checks['no_added_or_removed_SDK_sources']=actual==set(frozen)
    old=json.loads((ROOT/'results/phase4/evidence-manifest.json').read_text())
    for path,info in old['artifacts'].items():
        p=local(path)
        if not p.exists() or sha(p)!=info['sha256']:failures.append(path)
    checks['accepted_Phase4_artifacts_unchanged']=not failures
    baseline=old['legacy_baseline'];checks['old_SDK_binary_unchanged']=sha(local(baseline['path']))==baseline['sha256']
    published=[]
    for path,info in old['artifacts'].items():
        p=local(path);version=p.parent.parent.name;mc=p.parent.name
        actual=maven_local()/'net/funtimeevents/poc'/mc/version/p.name
        if actual.exists():published.append({'path':str(actual),'equal':sha(actual)==info['sha256']})
    checks['previous_MavenLocal_versions_unchanged']=all(p['equal'] for p in published)
    command=['git','diff','--exit-code','--','fte-api','build.gradle.kts','settings.gradle.kts','gradle.properties','gradle','gradlew','gradlew.bat']
    if shutil.which('rtk'):command=['rtk','proxy',*command]
    p=subprocess.run(command,cwd=ROOT.parent.parent,capture_output=True)
    checks['tracked_release_unchanged']=p.returncode==0 and not p.stdout.strip()
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'artifact_failures':failures,
            'previous_MavenLocal_files':published,'release_command':command,'release_diff':(p.stdout+p.stderr).decode(errors='replace')}
    (ROOT/'results/phase5/integrity.json').write_text(json.dumps(result,indent=2)+'\n');print(result['status'],checks)
    raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
