"""Restore downloaded TEST repository artifacts; never overwrite another build."""
import hashlib,json,shutil,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2]
version=json.loads((root/'targets.json').read_text())['sdk_version']
for mc in sys.argv[1].split(','):
    relative=Path('com/github/Rizzener/FunTimeEventsSDK')/('mc'+mc)/version
    source=root/'build/staging-maven'/relative
    assert source.is_dir(),source
    for path in source.iterdir():
        if path.suffix not in ['.jar','.pom','.module']:continue
        dest=Path.home()/'.m2/repository'/relative/path.name
        dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.exists():assert hashlib.sha256(dest.read_bytes()).digest()==hashlib.sha256(path.read_bytes()).digest(),dest
        else:shutil.copy2(path,dest)
