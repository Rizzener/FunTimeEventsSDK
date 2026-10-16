"""Public facade/config/models compatibility, not internal tracker constructors."""
import argparse, hashlib, json, re, subprocess, zipfile, shutil
from pathlib import Path
from run_phase2 import ROOT,maven_local

def signatures(jar, classes):
    command=(['rtk','proxy'] if shutil.which('rtk') else [])+['javap','-classpath',str(jar),'-public','-s',*classes]
    p=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace')
    if p.returncode:raise RuntimeError(p.stderr)
    result={};current=None;declaration=None
    for line in p.stdout.splitlines():
        text=line.strip()
        if text.startswith('public ') and text.endswith('{'):
            current=re.search(r'(?:class|interface|enum) ([^ <{]+)',text).group(1);result[current]=set()
        elif text.startswith('public '):declaration=text
        elif text.startswith('descriptor:') and declaration:
            name=re.search(r'([\w$<>]+)(?:\([^;]*\))?;$',declaration).group(1)
            result[current].add(name+' '+text.split(':',1)[1].strip());declaration=None
    return result,p.stdout

def main():
    p=argparse.ArgumentParser();p.add_argument('--version',required=True);p.add_argument('--targets');p.add_argument('--output');a=p.parse_args()
    release=ROOT.parents[1]/'fte-api'
    old=ROOT/'fixtures/binary-compat/legacy-sdk.jar'
    if not old.exists():old=release/'build/libs/fte-api-1.0.5-SNAPSHOT.jar'
    classes=['net.funtimeevents.api.FunTimeEventsAPI','net.funtimeevents.api.FteConfig',
             'net.funtimeevents.api.FteConfig$Builder','net.funtimeevents.api.FteConfig$LogLevel']
    with zipfile.ZipFile(old) as archive:
        classes += sorted(n[:-6].replace('/','.') for n in archive.namelist() if n.startswith('net/funtimeevents/model/') and n.endswith('.class') and '$' not in n)
    classes+=['net.funtimeevents.model.CaptchaResponse$PercentResult']
    classes+=['net.funtimeevents.net.ApiClient','net.funtimeevents.net.RelayClient',
              'net.funtimeevents.net.cache.RelayCache','net.funtimeevents.net.cache.SseCache',
              'net.funtimeevents.spi.PayloadSender','net.funtimeevents.util.FteLogger']
    baseline,raw=signatures(old,classes)
    out=ROOT/a.output if a.output else ROOT/'results/phase4/compat'/a.version;out.mkdir(parents=True,exist_ok=True)
    (out/'old-javap.txt').write_text(raw,encoding='utf-8')
    result={'version':a.version,'scope':classes,'old_sha256':hashlib.sha256(old.read_bytes()).hexdigest(),'targets':{}}
    targets=['1.21.4','26.3'] if not a.targets else [t['minecraft'] for t in json.loads((ROOT/'targets.json').read_text())['targets']] if a.targets=='all' else a.targets.split(',')
    for mc in targets:
        jar=maven_local()/'com/github/Rizzener/FunTimeEventsSDK'/('mc'+mc)/a.version/('mc'+mc+'-'+a.version+'.jar')
        actual,raw=signatures(jar,classes);(out/(mc+'-javap.txt')).write_text(raw,encoding='utf-8')
        missing={c:sorted(m-actual.get(c,set())) for c,m in baseline.items() if m-actual.get(c,set())}
        result['targets'][mc]={'missing':missing,'status':'PASS' if not missing else 'FAIL'}
        # Compile the exact old source against each new API as an independent source check.
        command=(['rtk','proxy'] if shutil.which('rtk') else [])+['javac','--release','21','-cp',str(jar),'-d',str(out/(mc+'-source')),
                 str(ROOT/'fixtures/binary-compat/LegacyApiProbe.java')]
        r=subprocess.run(command,capture_output=True);(out/(mc+'-source.txt')).write_bytes(r.stdout+r.stderr)
        result['targets'][mc]['source_compile_exit']=r.returncode
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,indent=2));raise SystemExit(any(v['status']!='PASS' or v['source_compile_exit'] for v in result['targets'].values()))
if __name__=='__main__':main()
