"""Keep only active root runners; all historical tests remain in immutable PoC."""
import ast,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    keep=set('phase5_build phase5_api phase5_verify phase5_run phase5_headless_check phase5_space verify_artifacts verify_phase4 verify_phase4_compat run_phase2 run_phase3 compile_legacy_probe prepare_phase4_tls phase6_artifacts phase6_provenance phase6_source_parity'.split())
    changed=True
    while changed:
        changed=False
        for name in list(keep):
            path=ROOT/(name+'.py')
            for node in ast.walk(ast.parse(path.read_text(encoding='utf-8'))):
                imports=[node.module] if isinstance(node,ast.ImportFrom) else [a.name for a in node.names] if isinstance(node,ast.Import) else []
                for module in imports:
                    if module and '.' not in module and (ROOT/(module+'.py')).exists() and module not in keep:
                        keep.add(module);changed=True
    removed=[]
    for p in (ROOT/'poc/multiversion').glob('*.py'):
        dest=ROOT/p.name
        if p.stem not in keep and dest.exists():
            assert dest.parent==ROOT and p.is_file()
            removed.append(p.name);dest.unlink()
    out=ROOT/'results/phase6/harness-layout.json';out.write_text(json.dumps({'active':sorted(keep),'historical_only_in_poc':removed},indent=2)+'\n')
    print('ACTIVE_RUNNERS',len(keep),'HISTORICAL_IN_POC',len(removed))
if __name__=='__main__':main()
