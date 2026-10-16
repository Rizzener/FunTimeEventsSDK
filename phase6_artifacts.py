"""Release hygiene and publication metadata, in addition to production JIJ checks."""
import argparse,hashlib,json,os,zipfile
from pathlib import Path
from run_phase2 import ROOT,maven_local

def one(mc):
    config=json.loads((ROOT/'targets.json').read_text());version=config['sdk_version']
    folder=maven_local()/config['maven_group'].replace('.','/')/('mc'+mc)/version
    checks={};hashes={}
    for suffix in ['.jar','-sources.jar','.pom','.module']:
        p=folder/('mc'+mc+'-'+version+suffix)
        checks['present_'+suffix]=p.is_file()
        if p.is_file():hashes[p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
    if all(checks.values()):
        with zipfile.ZipFile(folder/('mc'+mc+'-'+version+'.jar')) as z:
            names=z.namelist();classes=[n for n in names if n.endswith('.class')]
            checks['no_experimental_or_test_classes']=all(not any(s in n.lower() for s in ['/poc/','/verification/','/fixture/','trackersliceapi','bansliceapi','poceventsapi','poccore']) for n in names)
            checks['no_test_keys_certificates']=all(not n.endswith(('.pem','.p12','.key','.crt')) and not n.startswith(('phase2/','phase3/','phase4/')) for n in names) and all(b'phase4-local-key' not in z.read(n) and b'phase4-test' not in z.read(n) and b'127.0.0.1' not in z.read(n) for n in classes)
            checks['no_duplicate_entries']=len(names)==len(set(names))
            checks['license_preserved']=z.read('LICENSE')==(ROOT/'LICENSE').read_bytes()
            metadata=json.loads(z.read('fabric.mod.json'))
            checks['real_mod_id']=metadata['id']=='fte-api'
            checks['explicit_loader_api_dependencies']=metadata['depends']['fabricloader']=='>='+config['loader'] and metadata['depends']['fabric-api']=='*'
        module=json.loads((folder/('mc'+mc+'-'+version+'.module')).read_text())
        checks['module_coordinates']=module['component']=={'group':config['maven_group'],'module':'mc'+mc,'version':version,'attributes':{'org.gradle.status':'release'}}
        spec=next(t for t in config['targets'] if t['minecraft']==mc)
        checks['java_variant_constraint']=all(v['attributes'].get('org.gradle.jvm.version')==spec['java'] for v in module['variants'] if v['name'] in ['apiElements','runtimeElements'])
    value={'status':'PASS' if checks and all(checks.values()) else 'FAIL','checks':checks,'sha256':hashes}
    out=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/mc/'release-artifacts.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(value,indent=2)+'\n')
    print(mc,'RELEASE_ARTIFACTS',value['status'],[k for k,v in checks.items() if not v]);return value

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);a=p.parse_args()
    raise SystemExit(any(one(mc)['status']!='PASS' for mc in a.targets.split(',')))
