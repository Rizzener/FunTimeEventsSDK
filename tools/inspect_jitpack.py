"""Read only versions already reported built OK; never query the new RC."""
import json,urllib.request,urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    base='https://jitpack.io/'
    paths=['api/builds/com.github.Rizzener/FunTimeEventsSDK',
           'api/builds/com.github.Rizzener/FunTimeEventsSDK/v1.0.5',
           'com/github/Rizzener/FunTimeEventsSDK/fte-api/v1.0.5/fte-api-v1.0.5.pom',
           'com/github/Rizzener/FunTimeEventsSDK/v1.0.5/FunTimeEventsSDK-v1.0.5.pom',
           'com/github/Rizzener/FunTimeEventsSDK/v1.0.5/build.log']
    out=ROOT/'results/phase6/jitpack-existing';out.mkdir(parents=True,exist_ok=True);rows=[]
    for i,path in enumerate(paths):
        url=base+path
        try:
            with urllib.request.urlopen(url,timeout=30) as r:status=r.status;body=r.read()
        except urllib.error.HTTPError as e:status=e.code;body=e.read()
        target=out/(str(i)+'.txt');target.write_bytes(body)
        rows.append({'url':url,'status':status,'body_file':str(target.relative_to(ROOT)),'bytes':len(body)})
        print(status,url,body.decode(errors='replace')[:900] if 'api/builds' in path or path.endswith('.pom') else f'{len(body)} bytes')
    (out/'result.json').write_text(json.dumps(rows,indent=2)+'\n')
if __name__=='__main__':main()
