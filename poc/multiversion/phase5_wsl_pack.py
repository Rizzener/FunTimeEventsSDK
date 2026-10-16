"""Local NTFS -> WSL bulk transfer of immutable inputs; no remote destination."""
import hashlib,json,tarfile,time
from pathlib import Path
from run_phase2 import ROOT
source=ROOT/'.evidence/phase5/wsl/gradle'
inputs=json.loads((ROOT/'results/phase5/local-environment/wsl-inputs.json').read_text())['immutable_input_files']
archive=ROOT/'.evidence/phase5/wsl-native-inputs.tar'
selected=[r for r in inputs if not r['path'].startswith('caches/fabric-loom/assets/')]
started=time.time()
with tarfile.open(archive,'w') as output:
    for i,row in enumerate(selected):
        output.add('\\\\?\\'+str((source/row['path']).resolve()),arcname=row['path'],recursive=False)
        if i%1000==0:print('Local archive',i,'/',len(selected),flush=True)
hash=hashlib.sha256()
with archive.open('rb') as stream:
    for block in iter(lambda:stream.read(4*1024*1024),b''):hash.update(block)
(ROOT/'results/phase5/local-environment/wsl-bulk-inputs.json').write_text(json.dumps({'files':len(selected),'bytes':archive.stat().st_size,'sha256':hash.hexdigest(),'seconds':time.time()-started,'destination':'Local WSL native filesystem; never uploaded'},indent=2)+'\n')
print('Local input archive ready',len(selected),archive.stat().st_size,flush=True)
