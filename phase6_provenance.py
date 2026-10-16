"""Reject artifacts/evidence from other commits, workflow runs or attempts."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent
PREFIX=ROOT/os.environ.get('FTE_PHASE5_EVIDENCE','results/phase6')

def identity():
    sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    return {'sha':sha,'run_id':os.environ.get('GITHUB_RUN_ID'),'run_attempt':os.environ.get('GITHUB_RUN_ATTEMPT'),
            'version':json.loads((ROOT/'targets.json').read_text())['sdk_version']}

def main():
    p=argparse.ArgumentParser();p.add_argument('--target');p.add_argument('--summary',action='store_true');a=p.parse_args()
    expected=identity()
    if a.summary:
        for t in json.loads((ROOT/'targets.json').read_text())['targets']:
            value=json.loads((PREFIX/t['minecraft']/'provenance.json').read_text())
            assert value['identity']==expected,(t['minecraft'],value['identity'],expected)
            assert value['status']=='PASS'
        print('PROVENANCE_13_PASS',expected);return
    artifacts=json.loads((PREFIX/a.target/'release-artifacts.json').read_text())
    assert artifacts['status']=='PASS'
    for kind in ['artifacts','network','runtime','api']:
        assert json.loads((PREFIX/a.target/(kind+'.json')).read_text())['status']=='PASS',kind
    assert json.loads((PREFIX/a.target/'doc-examples/result.json').read_text())['status']=='PASS'
    value={'status':'PASS','identity':expected,'artifact_sha256':artifacts['sha256']}
    (PREFIX/a.target/'provenance.json').write_text(json.dumps(value,indent=2)+'\n')
    print('PROVENANCE_PASS',a.target,expected)

if __name__=='__main__':main()
