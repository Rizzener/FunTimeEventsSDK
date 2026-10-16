"""Adapt retained test harnesses to the promoted Maven coordinates and source paths."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]

def replace(relative, replacements):
    path=ROOT/relative
    text=path.read_text(encoding='utf-8')
    for old,new in replacements.items():
        if old not in text: raise ValueError((relative,old))
        text=text.replace(old,new)
    path.write_text(text,encoding='utf-8',newline='\n')

def main():
    replace('compile_legacy_probe.py',{'com/github/Rizzener/FunTimeEventsSDK/compat/':'net/funtimeevents/poc/compat/'})
    replace('run_phase3.py',{'com/github/Rizzener/FunTimeEventsSDK/fixture/':'net/funtimeevents/poc/fixture/'})
    replace('verify_artifacts.py',{
        'com/github/Rizzener/FunTimeEventsSDK/api/PocEventsAPI.class':'net/funtimeevents/bootstrap/Bootstrap.class',
        'com/github/Rizzener/FunTimeEventsSDK/core/PocCore.class':'net/funtimeevents/model/BanPayload.class',
        'isolated mod id':'release mod id',
    })
    for filename in ['verify_phase2.py','verify_phase3.py']:
        replace(filename,{'com/github/Rizzener/FunTimeEventsSDK/fixture/':'net/funtimeevents/poc/fixture/'})
    replace('gradle/target.gradle',{
        "licenses { license { name='MIT'; url='https://opensource.org/licenses/MIT' } }":
        "licenses { license { name='MIT License with Backend Restriction'; url='https://github.com/Rizzener/FunTimeEventsSDK/blob/main/LICENSE' } }",
        "from(project(':core').sourceSets.main.output)":"from(project(':core').sourceSets.main.output)\n    from(rootProject.file('LICENSE'))",
    })
    replace('platform/common/src/main/resources/fabric.mod.json',{'"license": "MIT"':'"license": "LicenseRef-FunTimeEvents-Backend-Restriction"'})
    # The old tiny core check remains a historical test, outside shipped classes.
    replace('platform/common/src/main/java/net/funtimeevents/internal/tracker/TrackerMetrics.java',{
        'Opt-in fixture diagnostics.':'Opt-in internal diagnostics.'})
    # Module metadata must match the required Java version even though core is Java 21.
    # Correct source parity checks explicitly translate only internal moves/mod identity.
    source=(ROOT/'phase5_api.py').read_text()
    start=source.index('def sources():')
    end=source.index("if __name__=='__main__':")
    source=source[:start]+'''def sources():
    from phase6_source_parity import verify
    return verify()

'''+source[end:]
    (ROOT/'phase5_api.py').write_text(source,encoding='utf-8',newline='\n')
    print('Release check paths adapted')

if __name__=='__main__':main()
