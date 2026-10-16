"""The everyday thirteen-target Gradle build must match independent matrix outputs."""
import hashlib,json,os
from run_phase2 import ROOT
def main():
    c=json.loads((ROOT/'targets.json').read_text());checks={};differences={}
    for t in c['targets']:
        mc=t['minecraft'];relative=c['maven_group'].replace('.','/')+'/mc'+mc+'/'+c['sdk_version']
        for suffix in ['.jar','-sources.jar','.pom','.module']:
            name='mc'+mc+'-'+c['sdk_version']+suffix
            first=ROOT/'build/staging-maven'/relative/name;second=ROOT/'build/aggregate-maven'/relative/name
            equal=first.exists() and second.exists() and hashlib.sha256(first.read_bytes()).digest()==hashlib.sha256(second.read_bytes()).digest()
            checks[mc+suffix]=equal
            if not equal:differences[mc+suffix]={'matrix_present':first.exists(),'aggregate_present':second.exists()}
    value={'status':'PASS' if all(checks.values()) and len(checks)==52 else 'FAIL','checks':checks,'differences':differences}
    out=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/'aggregate-comparison.json';out.write_text(json.dumps(value,indent=2)+'\n')
    print('AGGREGATE_13_TARGETS',value['status'],differences);raise SystemExit(value['status']!='PASS')
if __name__=='__main__':main()
