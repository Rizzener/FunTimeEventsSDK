"""Check the already-published old JitPack JAR against the retained API baseline."""
import argparse,hashlib,json,shutil,subprocess,sys,time,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from verify_phase4_compat import signatures
from run_phase2 import gradle,terminate_owned
from run_phase3 import diagnostics

def coexist(mc,published):
    cfg=json.loads((ROOT/'targets.json').read_text());version=cfg['sdk_version']
    spec=next(t for t in cfg['targets'] if t['minecraft']==mc)
    base=ROOT/'results/phase6/published-legacy/coexistence'/mc
    out=base/('attempt-%02d'%(len(list(base.glob('attempt-*')))+1));out.mkdir(parents=True)
    mods=out/'mods';mods.mkdir()
    libs=ROOT/'consumers'/('mc'+mc)/'build/libs'
    main=next(p for p in libs.glob('*-'+version+'.jar') if 'peer' not in p.name)
    peer=next(libs.glob('*-'+version+('-peer-production.jar' if spec['java']==21 else '-peer.jar')))
    shutil.copy2(main,mods/'main-consumer.jar')
    with zipfile.ZipFile(peer) as source,zipfile.ZipFile(mods/'published-legacy-peer.jar','w',zipfile.ZIP_DEFLATED) as target:
        metadata=json.loads(source.read('fabric.mod.json'));metadata['depends']['fte-api']='*'
        metadata['jars']=[{'file':'META-INF/jars/'+published.name}]
        for name in source.namelist():
            if name!='fabric.mod.json' and not name.startswith('META-INF/jars/'):target.writestr(name,source.read(name))
        target.writestr('fabric.mod.json',json.dumps(metadata));target.write(published,'META-INF/jars/'+published.name)
    observed=ROOT/'consumers'/('mc'+mc)/'build/production-run/phase4-api-control.json'
    observed.unlink(missing_ok=True)
    command=gradle('-p','consumers/mc'+mc,'prodClient','-Pmod_version='+version,'-Pphase4stage=a','-Pphase4ApiOnly',
        '-Pstaging_repository='+str(ROOT/'build/staging-maven'),'-Pphase6_mod_file='+str(mods))
    started=time.time();timeout=False
    with (out/'client.txt').open('wb') as log:
        process=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        try:process.wait(timeout=300)
        except subprocess.TimeoutExpired:timeout=True
        finally:terminate_owned(process)
    diagnostics(observed.parent,out,started)
    raw=(out/'client.txt').read_text(encoding='utf-8',errors='replace')
    fresh=observed.exists() and observed.stat().st_mtime>=started-2
    data=json.loads(observed.read_text()) if fresh else {}
    checks={'normal_exit':not timeout and process.returncode==0,'fresh_production_result':fresh,
            'one_SDK_peer_pass':'PEER_MOD_PASS sdk_copies=1' in raw,'old_binary_pass':'OLD_BINARY_PASS' in raw,
            'new_SDK_selected':data.get('sdk_version')==version,'nested_production':data.get('origin')=='NESTED' and data.get('development') is False,
            'target_java_namespace':data.get('java')==spec['java'] and data.get('namespace')==spec['namespace']}
    value={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'observed':data,'command':command,'exit':process.returncode,
           'published_old_sdk_sha256':hashlib.sha256(published.read_bytes()).hexdigest(),'main_consumer_sha256':hashlib.sha256(main.read_bytes()).hexdigest(),
           'scope':'Old published nested SDK with wildcard requirement plus exact new target SDK; retained old precompiled API probe. Peer test class uses the same verified public ABI, not an unknown third-party mod.'}
    (out/'result.json').write_text(json.dumps(value,indent=2)+'\n');print(mc,'PUBLISHED_LEGACY_COEXISTENCE',value['status'],checks)
    return value['status']=='PASS'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--coexist-target');args=parser.parse_args()
    out=ROOT/'results/phase6/published-legacy';out.mkdir(parents=True,exist_ok=True)
    jar=out/'FunTimeEventsSDK-v1.0.5.jar'
    url='https://jitpack.io/com/github/Rizzener/FunTimeEventsSDK/v1.0.5/FunTimeEventsSDK-v1.0.5.jar'
    if not jar.exists():
        with urllib.request.urlopen(url,timeout=30) as r:jar.write_bytes(r.read())
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='d8e26c08998eae48b398d6b15f61f10543334b5c25ce6b1242c4e7f2660529f9','Published legacy JAR changed; retaining for investigation'
    scope=json.loads((ROOT/'results/phase6/1.21.4/api-details/result.json').read_text())['scope']
    old,_=signatures(ROOT/'fixtures/binary-compat/legacy-sdk.jar',scope);published,raw=signatures(jar,scope)
    (out/'published-javap.txt').write_text(raw)
    checks={'same_36_public_descriptors':old==published}
    classes=out/'classes';classes.mkdir(exist_ok=True)
    command=['javac','--release','21','-cp',str(jar),'-d',str(classes),str(ROOT/'fixtures/binary-compat/LegacyApiProbe.java')]
    result=subprocess.run(command,capture_output=True);(out/'javac.txt').write_bytes(result.stdout+result.stderr)
    checks['old_source_compiles_on_published_JAR']=result.returncode==0
    probe=classes/'net/funtimeevents/poc/compat/LegacyApiProbe.class'
    checks['same_precompiled_probe_bytes']=probe.exists() and hashlib.sha256(probe.read_bytes()).hexdigest()=='ab32f192195cdb8ad7042b9509e0956a16ef73cbc8d31ddf22b562f07d3fc088'
    value={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'url':url,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'scope':scope,'command':command}
    (out/'result.json').write_text(json.dumps(value,indent=2)+'\n');print(value['status'],checks,'published SHA',value['jar_sha256'])
    valid=value['status']=='PASS'
    if args.coexist_target and valid:
        outcomes=[coexist(mc,jar) for mc in args.coexist_target.split(',')]
        valid=all(outcomes)
    raise SystemExit(not valid)
if __name__=='__main__':main()
