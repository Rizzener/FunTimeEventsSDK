"""Verify frozen published pure-Java sources against the current candidate workspace."""
import argparse, json, zipfile
from pathlib import Path
from run_phase2 import ROOT

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--version',default='0.0.9-phase4c');a=p.parse_args()
    checks={}
    for mc in ['1.21.4','26.3']:
        jar=Path.home()/'.m2/repository/net/funtimeevents/poc'/('mc'+mc)/a.version/('mc'+mc+'-'+a.version+'-sources.jar')
        with zipfile.ZipFile(jar) as archive:
            checks[mc]={}
            for folder in ['core/src/main/java','platform/common/src/main/java']:
                for source in (ROOT/folder).rglob('*.java'):
                    text=source.read_text(encoding='utf-8')
                    if 'import net.minecraft.' in text or 'import com.mojang.blaze3d.' in text:continue
                    name=source.relative_to(ROOT/folder).as_posix()
                    checks[mc][name]=name in archive.namelist() and archive.read(name).decode('utf-8').replace('\r\n','\n')==text
    output=ROOT/'results/phase4/4C/frozen-sources.json';output.write_text(json.dumps(checks,indent=2)+'\n',encoding='utf-8')
    failures=[mc+':'+n for mc,c in checks.items() for n,ok in c.items() if not ok]
    print('Frozen candidate sources',sum(map(len,checks.values()))-len(failures),'/',sum(map(len,checks.values())),failures)
    raise SystemExit(bool(failures))
