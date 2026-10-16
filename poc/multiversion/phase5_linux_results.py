"""Aggregate independent local Linux workers without mixing incomplete jobs."""
import json
from run_phase2 import ROOT

PREFIXES=['results/phase5/wsl/native','results/phase5/wsl/native-b']
def read(path):return json.loads(path.read_text(encoding='utf-8'))
def candidate(mc,prefix):
    base=ROOT/prefix/mc;row={'prefix':prefix}
    for kind in ['compile','publish','consumer']:
        row[kind]='PASS' if any(read(p).get('status')=='PASS' for p in base.glob(kind+'/attempt-*/result.json')) else 'NOT VERIFIED'
    for kind in ['artifacts','api','network','runtime']:
        path=base/(kind+'.json');row[kind]=read(path)['status'] if path.exists() else 'NOT VERIFIED'
    row['status']='PASS' if all(row[k]=='PASS' for k in ['compile','publish','consumer','artifacts','api','network','runtime']) else 'NOT VERIFIED'
    return row
def selected(mc):
    rows=[candidate(mc,p) for p in PREFIXES]
    return max(rows,key=lambda r:(r['status']=='PASS',sum(v=='PASS' for v in r.values())))
def main():
    config=read(ROOT/'targets.json');rows={t['minecraft']:selected(t['minecraft']) for t in config['targets']}
    result={'status':'PASS' if all(r['status']=='PASS' for r in rows.values()) else 'NOT VERIFIED','targets':rows,
            'selection':'Every target must have a complete compile/publication/consumer/API/artifact/network/four-mode runtime result in one worker. Incomplete jobs are not mixed.'}
    (ROOT/'results/phase5/linux-summary.json').write_text(json.dumps(result,indent=2)+'\n')
    print('Linux',sum(r['status']=='PASS' for r in rows.values()),'/13',result['status'])
    return result
if __name__=='__main__':main()
