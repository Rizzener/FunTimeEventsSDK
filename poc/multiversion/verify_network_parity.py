"""Compare legacy and ported wire messages from the identical Java harness."""
import argparse, base64, gzip, json
from pathlib import Path
from run_phase2 import ROOT

def main():
    p=argparse.ArgumentParser();p.add_argument('--stage',choices=['b','c'],default='b');a=p.parse_args()
    parent=ROOT/'results/phase4'/('4'+a.stage.upper())/'parity'
    attempts=sorted(p for p in parent.glob('attempt-*') if json.loads((p/'result.json').read_text())['status']=='PASS')
    output=attempts[-1];records=[json.loads(l) for l in (output/'raw.jsonl').read_text(encoding='utf-8').splitlines()]
    profiles={};checks={}
    for label in ['legacy','ported-1.21.4','ported-26.3']:
        profile={}
        for role,prefix in [('gzip','Parity/'),('plain','ParityPlain/')]:
            posts=[r for r in records if r['kind']=='post' and r.get('user_agent')==prefix+label]
            profile[role]=sorted([{'path':r['path'],'payload':r['payload']} for r in posts],key=lambda r:r['path'])
            checks[label+'_'+role+'_all_posts']=len(posts)==8 and {r['type'] for r in posts}=={'tab','ban','copper','warden','hell','mine','coordinates','captcha'}
            raw=[r for r in records if r['kind']=='http' and r['method']=='POST' and r['headers'].get('User-Agent')==prefix+label]
            checks[label+'_'+role+'_headers_bytes']=len(raw)==8 and all(
                r['headers'].get('Content-Type')=='application/json' and r['headers'].get('X-API-Key')=='phase4-local-key'
                and (r['headers'].get('Content-Encoding')=='gzip')==(role=='gzip')
                and (base64.b64decode(r['raw_base64'])[:2]==b'\x1f\x8b')==(role=='gzip') for r in raw)
            for r in raw:
                data=base64.b64decode(r['raw_base64']);json.loads(gzip.decompress(data) if role=='gzip' else data)
        ws=[r for r in records if r['kind']=='ws' and r.get('user_agent')=='Parity/'+label]
        checks[label+'_WS_auth_text']=len(ws)==8 and all(r['frame']=='TEXT' for r in ws) and any(r['envelope']=={'type':'auth','api_key':'phase4-local-key'} for r in ws)
        profile['ws']=sorted([r['envelope'] for r in ws if r['envelope']['type']!='auth'],key=lambda e:e['type'])
        profiles[label]=profile
    checks['legacy_equals_ported_21']=profiles['legacy']==profiles['ported-1.21.4']
    checks['legacy_equals_ported_25']=profiles['legacy']==profiles['ported-26.3']
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'profiles':profiles}
    (output/'wire-comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(result['status'],sum(checks.values()),'/',len(checks),[k for k,v in checks.items() if not v]);raise SystemExit(result['status']!='PASS')
if __name__=='__main__':main()
