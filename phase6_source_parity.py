"""Semantic migration guard: explicit moves only; no tracker or transport rewrites."""
import hashlib,json,os,zipfile
from pathlib import Path
from tools.integrate_phase6 import REPLACEMENTS

ROOT=Path(__file__).resolve().parent

def normalize(text):
    for old,new in REPLACEMENTS.items():text=text.replace(old,new)
    return text.replace('\r\n','\n')

def verify():
    old=ROOT/'fixtures/binary-compat/phase4-source-baseline.jar'
    checks={};exceptions={}
    excluded={'PocEventsAPI.java','BanSliceAPI.java','TrackerSliceAPI.java','PocCore.java'}
    with zipfile.ZipFile(old) as z:
        for name in z.namelist():
            if not name.endswith('.java') or name.rsplit('/',1)[-1] in excluded:continue
            if name.endswith(('/VersionAccess.java','/HoverAccess.java')):
                exceptions[name]='Accepted Phase 5 leaf extraction; separately compiled for every target';continue
            mapped=normalize(name)
            p=next((ROOT/base/mapped for base in ['core/src/main/java','platform/common/src/main/java'] if (ROOT/base/mapped).exists()),None)
            if p is None:
                checks[name]=False;continue
            expected=normalize(z.read(name).decode('utf-8'))
            if name.endswith('/TrackerMetrics.java'):
                expected=expected.replace('Opt-in fixture diagnostics.','Opt-in internal diagnostics.')
            checks[name]=expected==p.read_text(encoding='utf-8')
    for leaf in ['hover-legacy','hover-record','profile-getters','profile-record','dimension-location','dimension-identifier','boss-gui','boss-hud']:
        for p in (ROOT/'poc/multiversion/platform'/leaf).rglob('*.java'):
            name=p.relative_to(ROOT/'poc/multiversion').as_posix()
            dest=ROOT/normalize(name)
            checks[name]=dest.exists() and normalize(p.read_text(encoding='utf-8'))==dest.read_text(encoding='utf-8')
    required=['BanTracker','DungeonTracker','HellMapTracker','TabTracker','MineTracker','EventCoordinatesTracker','ServerContext','ApiClient','RelayClient','RelayCache','SseCache','PayloadQueue','SessionFence','FunTimeEventsAPI','FteConfig']
    coverage={n:any(k.endswith('/'+n+'.java') for k in checks) for n in required}
    result={'status':'PASS' if checks and all(checks.values()) and all(coverage.values()) else 'FAIL',
            'baseline_sha256':hashlib.sha256(old.read_bytes()).hexdigest(),'checks':checks,'required_coverage':coverage,'exceptions':exceptions,
            'allowed_transforms':REPLACEMENTS}
    out=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')/'source-parity.json'
    out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n')
    print('SOURCE_PARITY',result['status'],len(checks),'failed',[n for n,v in checks.items() if not v],flush=True)
    return result

if __name__=='__main__':raise SystemExit(verify()['status']!='PASS')
