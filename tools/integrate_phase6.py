"""One-time, reviewable promotion of the accepted architecture. Never edits PoC."""
from pathlib import Path
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / 'poc/multiversion'
REPLACEMENTS = {
    'net.funtimeevents.poc.slice': 'net.funtimeevents.internal.game',
    'net/funtimeevents/poc/slice': 'net/funtimeevents/internal/game',
    'net.funtimeevents.poc.platform': 'net.funtimeevents.internal.platform',
    'net/funtimeevents/poc/platform': 'net/funtimeevents/internal/platform',
    'net.funtimeevents.poc.trackers': 'net.funtimeevents.internal.tracker',
    'net/funtimeevents/poc/trackers': 'net/funtimeevents/internal/tracker',
    'FabricBanPlatform': 'FabricPlatform',
    'BanSliceRuntime': 'GameSessionRuntime',
    'fte-multiversion-poc': 'fte-api',
    'net.funtimeevents.poc.consumer': 'net.funtimeevents.verification.consumer',
    'net/funtimeevents/poc/consumer': 'net/funtimeevents/verification/consumer',
    'fte.phase3.metrics': 'fte.diagnostics.metrics',
}

def transform(text):
    for old, new in REPLACEMENTS.items():
        text = text.replace(old, new)
    return text

def main():
    receipt = ROOT / 'results/phase6/integration.json'
    if receipt.exists():
        raise SystemExit('Integration already performed; edit the promoted source directly.')
    changes = []
    folders = ['core', 'platform', 'targets', 'consumers', 'fixtures', 'verification', 'gradle', 'ci']
    files = [p for folder in folders for p in (OLD/folder).rglob('*') if p.is_file()
             and not any(s in p.relative_to(OLD/folder).parts for s in ['build', '.gradle', 'logs', 'run', '__pycache__'])]
    files += list(OLD.glob('*.py'))
    files += [OLD/n for n in ['settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','targets.json']]
    for source in files:
        relative = source.relative_to(OLD)
        target = ROOT / transform(relative.as_posix())
        target.parent.mkdir(parents=True, exist_ok=True)
        if source.suffix in ['.java','.json','.py','.kts','.gradle','.properties','.sh','.yml','.md','.txt'] or source.name == 'gradlew':
            target.write_text(transform(source.read_text(encoding='utf-8')), encoding='utf-8', newline='\n')
        else:
            shutil.copy2(source, target)
        changes.append({'from':source.relative_to(ROOT).as_posix(),'to':target.relative_to(ROOT).as_posix(),
                        'old_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
                        'new_sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
    # Historical harness entrypoints belong to consumer tests, never to SDK JARs.
    for name in ['PocEventsAPI','BanSliceAPI','TrackerSliceAPI']:
        source = ROOT/f'platform/common/src/main/java/net/funtimeevents/poc/api/{name}.java'
        target = ROOT/f'consumers/common/src/main/java/net/funtimeevents/poc/api/{name}.java'
        target.parent.mkdir(parents=True,exist_ok=True)
        source.replace(target)
    source = ROOT/'core/src/main/java/net/funtimeevents/poc/core/PocCore.java'
    for directory in ['core/src/verification/java','consumers/common/src/main/java']:
        target = ROOT/directory/'net/funtimeevents/poc/core/PocCore.java'
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source,target)
    source.unlink()
    config=json.loads((ROOT/'targets.json').read_text())
    config['sdk_version']='2.0.0-rc.1'
    config['maven_group']='com.github.Rizzener.FunTimeEventsSDK'
    (ROOT/'targets.json').write_text(json.dumps(config,indent=2)+'\n')
    receipt.parent.mkdir(parents=True,exist_ok=True)
    receipt.write_text(json.dumps({'baseline':'99948f1','accepted_poc_commit':'d3a4f1e','files':changes},indent=2)+'\n')
    print('Promoted',len(changes),'files; original PoC untouched')

if __name__ == '__main__':
    main()
