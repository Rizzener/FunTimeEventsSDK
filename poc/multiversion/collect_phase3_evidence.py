"""Save portable text mirrors/manifests; never alter the original attempt logs."""
from pathlib import Path
import hashlib
import json
import re
from run_phase2 import ROOT

out=ROOT/'results/phase3';builds=out/'build';builds.mkdir(exist_ok=True)
for p in (ROOT/'.evidence').glob('phase3*.log'):
    data=p.read_bytes();encoding='utf-16' if data.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig'
    (builds/(p.stem+'.txt')).write_text(data.decode(encoding,errors='replace'),encoding='utf-8')
manifest={'version':'0.0.6-phase3','sdk_artifacts':{},'current_sources':{}}
for mc in ['1.21.4','26.3']:
    p=Path.home()/'.m2/repository/net/funtimeevents/poc'/('mc'+mc)/manifest['version']/('mc'+mc+'-'+manifest['version']+'.jar')
    manifest['sdk_artifacts'][mc]={'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
for directory in [ROOT/'core/src/main',ROOT/'platform']:
    for p in directory.rglob('*.java'):manifest['current_sources'][str(p.relative_to(ROOT))]=hashlib.sha256(p.read_bytes()).hexdigest()
(out/'evidence-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
native=[]
for p in sorted((out/'native-investigation').glob('*/result.json')):
    d=json.loads(p.read_text(encoding='utf-8'));log=(p.parent/'client.txt').read_text(encoding='utf-8',errors='replace')
    native.append({'path':str(p.relative_to(ROOT)),'status':d['status'],'sdk_listed':d.get('sdk_listed'),
        'consumer_listed':d.get('consumer_listed'),'trackers_started':d.get('trackers_started'),
        'native_codes':re.findall(r'NTSTATUS (0x[0-9A-Fa-f]+)',log),
        'pid':(d.get('tracked_pid') or {}).get('ProcessId'),
        'retained_dumps':sorted({r['path'] for r in d.get('dump_capture',[]) if 'path' in r})})
(out/'native-investigation/controls-summary.json').write_text(json.dumps(native,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Saved',len(list(builds.glob('*.txt'))),'build logs;',len(native),'native controls; SDK fingerprints')
