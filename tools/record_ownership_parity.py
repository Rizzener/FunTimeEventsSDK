"""Explicit review-time recording. Never called by CI or normal builds."""
import hashlib,json,zipfile,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from phase6_source_parity import normalize
ROOT=Path(__file__).resolve().parents[1]
if __name__=='__main__':
    record={}
    with zipfile.ZipFile(ROOT/'fixtures/binary-compat/phase4-source-baseline.jar') as z:
        for short in ['FunTimeEventsAPI.java','FteConfig.java','Bootstrap.java']:
            name=next(n for n in z.namelist() if n.endswith('/'+short))
            p=next((ROOT/'platform/common/src/main/java').rglob(short))
            record[short]={'baseline_sha256':hashlib.sha256(normalize(z.read(name).decode()).encode()).hexdigest(),
                           'candidate_sha256':hashlib.sha256(p.read_text(encoding='utf-8').encode()).hexdigest(),
                           'reason':'RuntimeLease ownership; additive Builder API, client executor handoff, generation and cleanup'}
    out=ROOT/'fixtures/release-hardening/ownership-parity.json';out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(record,indent=2)+'\n')
