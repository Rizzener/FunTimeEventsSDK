"""Inventory every old public class, including implementation APIs outside probe scope."""
import argparse,json,os,zipfile
from run_phase2 import ROOT,maven_local
from verify_phase4_compat import signatures
def main():
    p=argparse.ArgumentParser();p.add_argument('--targets',required=True);a=p.parse_args()
    old=ROOT/'fixtures/binary-compat/legacy-sdk.jar'
    with zipfile.ZipFile(old) as z:classes=sorted(n[:-6].replace('/','.') for n in z.namelist() if n.endswith('.class'))
    baseline,raw=signatures(old,classes);c=json.loads((ROOT/'targets.json').read_text())
    for mc in a.targets.split(','):
        jar=maven_local()/c['maven_group'].replace('.','/')/('mc'+mc)/c['sdk_version']/f"mc{mc}-{c['sdk_version']}.jar"
        with zipfile.ZipFile(jar) as z:present={n[:-6].replace('/','.') for n in z.namelist() if n.endswith('.class')}
        actual,raw=signatures(jar,[n for n in classes if n in present])
        missing={n:sorted(m-actual.get(n,set())) for n,m in baseline.items() if m-actual.get(n,set())}
        result={'old_public_classes':len(baseline),'missing_classes':[n for n in baseline if n not in present],
                'missing_descriptors':missing,'note':'Complete Java-public surface inventory; namespace-dependent/internal contracts require an explicit scope decision.'}
        out=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/mc/'compatibility-inventory.json';out.write_text(json.dumps(result,indent=2)+'\n')
        print(mc,'PUBLIC_CLASS_INVENTORY',len(baseline),'missing_classes',result['missing_classes'],'changed_classes',list(missing))
if __name__=='__main__':main()
