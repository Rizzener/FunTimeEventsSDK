"""One-time mechanical import, preserving the release tree and baseline evidence."""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent
RELEASE = ROOT.parents[1] / 'fte-api/src/main/java/net/funtimeevents'

def main():
    common = ROOT / 'platform/common/src/main/java/net/funtimeevents'
    core = ROOT / 'core/src/main/java/net/funtimeevents'
    for source in (RELEASE / 'model').glob('*.java'):
        target = core / 'model' / source.name
        if not target.exists(): shutil.copy2(source, target)
    for relative in ['api/FteConfig.java', 'api/FunTimeEventsAPI.java', 'util/FteLogger.java',
                     'net/ApiClient.java', 'net/RelayClient.java',
                     'net/cache/RelayCache.java', 'net/cache/SseCache.java']:
        target = common / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists(): shutil.copy2(RELEASE / relative, target)
        baseline = ROOT / 'fixtures/history/phase4-original' / (relative + '.txt')
        baseline.parent.mkdir(parents=True, exist_ok=True)
        if not baseline.exists(): shutil.copy2(RELEASE / relative, baseline)
    print('Imported missing source files; release and existing migration files unchanged')

if __name__ == '__main__': main()
