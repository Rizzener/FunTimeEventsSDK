"""Retain WER evidence belonging to this fixture crash, without changing Windows."""
import argparse,hashlib,importlib.util,json,re,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--attempt',required=True);a=p.parse_args()
    attempt=(ROOT/a.attempt).resolve();assert attempt.is_relative_to(ROOT/'results/phase6')
    events=json.loads((attempt/'windows-java-events.txt').read_text(encoding='utf-8'))
    if isinstance(events,dict):events=[events]
    messages='\n'.join(e['Message'] for e in events)
    assert 'jvm.dll' in messages and 'c0000005' in messages.lower(),'Unexpected native signature; do not misclassify'
    offset=re.search(r'Fault offset:\s*0x([a-f0-9]+)',messages,re.I).group(1)
    signature='0xC0000005 jvm.dll+0x'+offset.lstrip('0').upper()
    report=re.search(r'Report Id:\s*([a-f0-9-]+)',messages,re.I).group(1)
    pid=int(re.search(r'Faulting process id:\s*0x([a-f0-9]+)',messages,re.I).group(1),16)
    out=attempt/'native-capture';out.mkdir(exist_ok=True)
    files=[]
    def retain(source):
        target=out/source.name
        if not target.exists():shutil.copy2(source,target)
        files.append({'source':str(source),'file':str(target.relative_to(ROOT)),'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
    for source in Path('C:/ProgramData/Microsoft/Windows/WER/ReportArchive').glob('AppCrash_java.exe*/Report.wer'):
        content=source.read_text(encoding='utf-16',errors='replace')
        if report in content:retain(source)
    helper=importlib.util.spec_from_file_location('fte_native_dump',ROOT/'poc/multiversion/native_dump.py')
    module=importlib.util.module_from_spec(helper);helper.loader.exec_module(module)
    missing=[]
    for path in set(re.findall(r'C:\\ProgramData\\Microsoft\\Windows\\WER\\Temp\\[^\r\n]+',messages)):
        source=Path(path.strip())
        if not source.exists():missing.append(str(source));continue
        if source.suffix=='.dmp' and module.dump_pid(source)!=pid:raise RuntimeError('Refusing unrelated process dump')
        retain(source)
    value={'native_status':'OPEN','report_id':report,'pid':pid,'signature':signature,
           'files':files,'missing_temporary_files':missing,'scope':'Only correlated Java fixture WER report and attachments; no debugger/registry/JVM flag changes.'}
    (out/'capture.json').write_text(json.dumps(value,indent=2)+'\n');print(json.dumps(value,indent=2))
if __name__=='__main__':main()
