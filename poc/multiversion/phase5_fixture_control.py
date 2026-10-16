"""Read-only evidence for the 1.21.11 scene preparation experiment."""
import hashlib,json,zipfile
from pathlib import Path
from run_phase2 import ROOT

def snapshots(folder):
    text=(folder/'client.txt').read_text(encoding='utf-8',errors='replace')
    return [json.loads(line.split('WORLD_SNAPSHOT ',1)[1]) for line in text.splitlines() if 'WORLD_SNAPSHOT ' in line]

def nested_hashes(folder):
    result=[]
    for path in (folder/'artifacts').glob('*.jar'):
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                if name.startswith('META-INF/jars/mc1.21.11-'):
                    result.append(hashlib.sha256(jar.read(name)).hexdigest())
    return result

def main():
    parent=ROOT/'results/phase5/1.21.11/runtime-rest'
    before=parent/'attempt-02';after=parent/'attempt-03'
    old=json.loads((before/'result.json').read_text(encoding='utf-8'))
    new=json.loads((after/'result.json').read_text(encoding='utf-8'))
    a=snapshots(before);b=snapshots(after)
    checks={'initial_run_failed':old['status']=='FAIL','control_full_runtime_passed':new['status']=='PASS',
            'same_published_SDK_in_both_consumers':set(nested_hashes(before))==set(nested_hashes(after))=={new['sdk_sha256']},
            'old_client_empty_chunk':all(c['kind']=='class_2812' for c in a[0]['chest_chunks']),
            'new_client_real_chunk':all(c['kind']=='class_2818' and not c['empty'] for c in b[0]['chest_chunks']),
            'new_client_timers':len([e for e in b[0]['text_displays'] if ':' in e['text']])==6}
    for fixture in ['phase3','phase4']:
        name='fixtures/data/'+fixture+'/tracker-sessions.json'
        with zipfile.ZipFile(before/'fixture-source.zip') as x,zipfile.ZipFile(after/'fixture-source.zip') as y:
            checks['same_'+fixture+'_golden']=x.read(name)==y.read(name)==(ROOT/name).read_bytes()
    result={'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'sdk_sha256':new['sdk_sha256'],
            'before':str(before.relative_to(ROOT)),'after':str(after.relative_to(ROOT)),
            'scope':'Fixture preparation only; internal cause of vanilla chunk scheduling not established; SDK and golden unchanged.'}
    (ROOT/'results/phase5/local-environment/chunk-diagnostics/control.json').write_text(json.dumps(result,indent=2)+'\n')
    print(result);raise SystemExit(result['status']!='PASS')

if __name__=='__main__':main()
