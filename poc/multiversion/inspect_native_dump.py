"""Inspect the retained Windows minidump without executing or loading its modules."""
from pathlib import Path
import argparse
import hashlib
import json
import struct
from native_dump import dump_pid


def inspect(path):
    data=path.read_bytes()
    if data[:4]!=b'MDMP':raise ValueError('Not a Windows minidump')
    count,directory=struct.unpack_from('<II',data,8)
    streams={kind:(size,rva) for i in range(count) for kind,size,rva in [struct.unpack_from('<III',data,directory+12*i)]}
    def string(rva):
        size=struct.unpack_from('<I',data,rva)[0]
        return data[rva+4:rva+4+size].decode('utf-16le',errors='replace')
    modules=[]
    if 4 in streams:
        _,rva=streams[4];n=struct.unpack_from('<I',data,rva)[0]
        for i in range(n):
            p=rva+4+108*i;base,size,checksum,stamp,name=struct.unpack_from('<QIIII',data,p)
            modules.append({'base':base,'size':size,'path':string(name)})
    names={}
    if 24 in streams:
        _,rva=streams[24];n=struct.unpack_from('<I',data,rva)[0]
        for i in range(n):
            tid,name=struct.unpack_from('<IQ',data,rva+4+12*i);names[tid]=string(name)
    result={'path':str(path.resolve()),'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'pid':dump_pid(path),'stream_types':sorted(streams),'module_count':len(modules)}
    if 6 in streams:
        _,rva=streams[6];tid=struct.unpack_from('<I',data,rva)[0]
        code=struct.unpack_from('<I',data,rva+8)[0];address=struct.unpack_from('<Q',data,rva+24)[0]
        module=next((m for m in modules if m['base']<=address<m['base']+m['size']),None)
        result['exception']={'thread_id':tid,'thread_name':names.get(tid),'code':hex(code),'address':hex(address),
            'module':module['path'] if module else None,'module_offset':hex(address-module['base']) if module else None}
    return result


def main():
    parser=argparse.ArgumentParser();parser.add_argument('dump',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args();result=inspect(args.dump);text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
    if args.output:args.output.write_text(text,encoding='utf-8')
    print(text)


if __name__=='__main__':main()
