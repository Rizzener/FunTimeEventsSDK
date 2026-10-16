"""Read only a minidump header/PID before retaining a dump of our tagged client."""
from pathlib import Path
import json
import shutil
import struct


def dump_pid(path):
    with path.open('rb') as f:
        header=f.read(32)
        if len(header)<32 or header[:4]!=b'MDMP':return None
        count,directory=struct.unpack_from('<II',header,8)
        if count>1000:return None
        f.seek(directory)
        entries=f.read(count*12)
        if len(entries)!=count*12:return None
        for index in range(count):
            kind,size,rva=struct.unpack_from('<III',entries,index*12)
            if kind==15 and size>=12:  # MiscInfoStream; PROCESS_ID flag.
                f.seek(rva);data=f.read(12)
                if len(data)==12:
                    _,flags,pid=struct.unpack('<III',data)
                    if flags&1:return pid
    return None


def retain_wer_dump(pid, started, destination):
    """No process-wide debugger/registry change; copy only a matching PID dump."""
    destination.mkdir(parents=True,exist_ok=True)
    records=[]
    for source in Path('C:/ProgramData/Microsoft/Windows/WER/Temp').glob('*.dmp'):
        try:
            if source.stat().st_mtime<started-2 or dump_pid(source)!=pid:continue
            target=destination/source.name
            shutil.copy2(source,target)
            records.append({'path':str(target),'pid':pid,'bytes':target.stat().st_size})
        except OSError as error:
            records.append({'source':str(source),'capture_error':str(error)})
    return records
