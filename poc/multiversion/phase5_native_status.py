"""Keep startup stability separate from functional SDK compatibility."""
import json
from run_phase2 import ROOT

def main():
    rows=[]
    for environment,prefix in [('Windows','results/phase5'),('native-WSL','results/phase5/wsl/native')]:
        root=ROOT/prefix/'26.3'
        verified=root/'runtime.json';checks=json.loads(verified.read_text()) if verified.exists() else {}
        valid={r['path'].replace('\\','/') for mode in checks.get('modes',{}).values() for r in mode['runs'] if r['status']=='PASS'}
        for path in sorted(root.glob('runtime-*/attempt-*/result.json')):
            if path.parent.parent.name not in ['runtime-offline','runtime-rest','runtime-ws','runtime-flags']:continue
            data=json.loads(path.read_text());text=(path.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
            rows.append({'environment':environment,'evidence':path.relative_to(ROOT).as_posix(),'recorded_status':data['status'],
                'normal_exit':data.get('client_exit')==0 and data.get('server_exit')==0,
                'strict_functional_pass':path.relative_to(ROOT).as_posix() in valid,
                'windows_native_access_violation':'0xc0000005' in text.lower() or '-1073741819' in text})
    linux=[r for r in rows if r['environment']=='native-WSL' and r['strict_functional_pass']]
    result={'native_status':'OPEN','linux_six_verified_fresh_production_runs':len(linux)>=6,'runs':rows,
        'scope':'Functional tests and process exits only. No claim that the native Windows issue is fixed; no SDK JVM/driver workaround.'}
    (ROOT/'results/phase5/native-startup.json').write_text(json.dumps(result,indent=2)+'\n')
    print('OPEN; verified Linux starts',len(linux),'Windows native failures',sum(r['windows_native_access_violation'] for r in rows))

if __name__=='__main__':main()
