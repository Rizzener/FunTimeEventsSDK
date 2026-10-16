"""Capture official release/Fabric metadata and propose pinned target configuration."""
import datetime, hashlib, io, json, re, shutil, traceback, urllib.request, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
from run_phase2 import ROOT

TARGETS=['1.21.4','1.21.5','1.21.6','1.21.7','1.21.8','1.21.9','1.21.10','1.21.11','26.1','26.1.1','26.1.2','26.2','26.3']

def main():
    parent=ROOT/'results/phase5/metadata';parent.mkdir(parents=True,exist_ok=True)
    output=parent/('attempt-%02d'%(len(list(parent.glob('attempt-*')))+1));output.mkdir()
    requests=[]
    def fetch(url,name):
        data=urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'FunTimeEventsSDK-Phase5-metadata/1'}),timeout=90).read()
        (output/name).write_bytes(data);requests.append({'url':url,'path':name,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)})
        (output/'requests.json').write_text(json.dumps(requests,indent=2)+'\n',encoding='utf-8')
        return data
    games=json.loads(fetch('https://meta.fabricmc.net/v2/versions/game','fabric-games.json'))
    launcher=json.loads(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json','minecraft-manifest.json'))
    versions=[e.text for e in ET.fromstring(fetch('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml','fabric-api.xml')).findall('./versioning/versions/version')]
    configuration={'gradle':'9.6.1','loom':'1.17.21','loader':'0.19.5','installer':'1.1.2','sdk_version':'0.1.0-phase5.1','targets':[]}
    observed=[]
    for mc in TARGETS:
        assert any(v['version']==mc and v['stable'] for v in games),mc+' absent/non-stable in Fabric metadata'
        entry=next(v for v in launcher['versions'] if v['id']==mc);assert entry['type']=='release'
        raw=fetch(entry['url'],'minecraft-'+mc+'.json');assert hashlib.sha1(raw).hexdigest()==entry['sha1']
        game=json.loads(raw);java=game['javaVersion']['majorVersion'];assert java==(21 if mc.startswith('1.') else 25)
        loader=json.loads(fetch('https://meta.fabricmc.net/v2/versions/loader/'+mc+'/0.19.5','loader-'+mc+'.json'))
        assert loader['loader']['version']=='0.19.5'
        pinned={'1.21.4':'0.113.0+1.21.4','26.3':'0.163.0+26.3'}.get(mc)
        family='26.1' if mc in ['26.1.1','26.1.2'] else mc
        choices=[v for v in versions if re.fullmatch(r'\d+\.\d+\.\d+\+'+re.escape(mc),v)]
        if not choices:choices=[v for v in versions if re.fullmatch(r'\d+\.\d+\.\d+\+'+re.escape(family),v)]
        api=pinned or max(choices,key=lambda v:tuple(map(int,v.split('+')[0].split('.'))))
        api_url='https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/'+api+'/fabric-api-'+api+'.jar'
        api_bytes=fetch(api_url,'fabric-api-'+mc+'.jar')
        with zipfile.ZipFile(io.BytesIO(api_bytes)) as jar:api_meta=json.loads(jar.read('fabric.mod.json'))
        descriptor=game['downloads']['client']
        client=Path.home()/'.gradle/caches/fabric-loom'/mc/'minecraft-client.jar'
        if not client.exists() or hashlib.sha1(client.read_bytes()).hexdigest()!=descriptor['sha1']:
            client=ROOT/'.evidence/phase5/minecraft'/(mc+'-client.jar');client.parent.mkdir(parents=True,exist_ok=True)
            if not client.exists() or hashlib.sha1(client.read_bytes()).hexdigest()!=descriptor['sha1']:
                with urllib.request.urlopen(descriptor['url'],timeout=120) as response,client.open('wb') as target:shutil.copyfileobj(response,target)
            assert hashlib.sha1(client.read_bytes()).hexdigest()==descriptor['sha1']
        with zipfile.ZipFile(client) as jar:internal=json.loads(jar.read('version.json'))
        (output/('version-'+mc+'.json')).write_text(json.dumps(internal,indent=2)+'\n',encoding='utf-8')
        legacy=java==21
        configuration['targets'].append({'minecraft':mc,'java':java,'fabric_api':api,'namespace':'intermediary' if legacy else 'official',
            'platform_sources':['common','legacy' if legacy else 'modern'],'fixture_sources':['common','legacy' if legacy else 'modern'],
            'data_pack':internal['pack_version'].get('data',internal['pack_version'].get('data_major')),
            'data_pack_minor':internal['pack_version'].get('data_minor',0),'split_pack_format':'data_major' in internal['pack_version']})
        observed.append({'minecraft':mc,'java':java,'api':api,'api_minecraft_predicate':api_meta['depends']['minecraft'],
            'client_sha1':descriptor['sha1'],'mappings_present':'client_mappings' in game['downloads'],'internal_version':internal})
        print(mc,'Java',java,'Fabric',api,'predicate',api_meta['depends']['minecraft'],'pack',internal['pack_version'],flush=True)
    (output/'targets.json').write_text(json.dumps(configuration,indent=2)+'\n',encoding='utf-8')
    (output/'result.json').write_text(json.dumps({'status':'PASS','at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'requests':requests,'targets':observed},indent=2)+'\n',encoding='utf-8')
    print(output,flush=True)

if __name__=='__main__':
    try:main()
    except Exception:
        output=sorted((ROOT/'results/phase5/metadata').glob('attempt-*'))[-1]
        (output/'result.json').write_text(json.dumps({'status':'FAIL','error':traceback.format_exc()},indent=2)+'\n',encoding='utf-8')
        raise
