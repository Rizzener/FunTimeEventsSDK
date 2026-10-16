"""Install only hash-pinned official Temurin archives into the CI image."""
import argparse,hashlib,json,shutil,tarfile,urllib.request
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--pins',type=Path,default=Path('/opt/phase5/jdks.json'));p.add_argument('--destination',type=Path,default=Path('/opt/jdks'));args=p.parse_args()
args.destination.mkdir(parents=True,exist_ok=True)
for item in json.loads(args.pins.read_text()):
    destination=args.destination/str(item['feature'])
    if (destination/'bin/javac').exists():continue
    archive=args.destination/('jdk'+str(item['feature'])+'.tar.gz')
    with urllib.request.urlopen(item['url'],timeout=180) as response,archive.open('wb') as target:shutil.copyfileobj(response,target)
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==item['sha256'],'JDK checksum mismatch'
    destination.mkdir(parents=True,exist_ok=True)
    with tarfile.open(archive) as source:
        prefix=source.getmembers()[0].name.split('/')[0]
        source.extractall(destination.parent,filter='data')
    extracted=destination.parent/prefix
    for child in extracted.iterdir():shutil.move(str(child),destination/child.name)
    extracted.rmdir();archive.unlink()
