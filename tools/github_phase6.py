"""Read hosted CI evidence using existing Git credentials; never prints credentials."""
import argparse,datetime,hashlib,json,os,shutil,subprocess,urllib.request,urllib.error,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
API='https://api.github.com/repos/Rizzener/FunTimeEventsSDK/'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def credential():
    env=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='Never')
    p=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=env,timeout=30)
    if p.returncode:raise RuntimeError('Existing GitHub credential unavailable')
    values=dict(line.split('=',1) for line in p.stdout.splitlines() if '=' in line)
    return values['password']

def request(path,token):
    assert path.startswith('actions/'),path
    return urllib.request.Request(API+path,headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'FunTimeEventsSDK-Phase6-Evidence'})

def get(path,token):
    with urllib.request.urlopen(request(path,token),timeout=60) as r:return json.load(r)

def download(path,token,target):
    try:
        response=urllib.request.build_opener(NoRedirect).open(request(path,token),timeout=60)
    except urllib.error.HTTPError as e:
        if e.code!=302:raise
        location=e.headers['Location'];assert location.startswith('https://')
        # Signed artifact/log URLs are fetched without GitHub Authorization.
        response=urllib.request.urlopen(location,timeout=60)
    partial=target.with_name(target.name+'.part')
    with response,partial.open('wb') as f:shutil.copyfileobj(response,f)
    partial.replace(target)

def main():
    p=argparse.ArgumentParser();p.add_argument('--run-id',type=int,required=True);p.add_argument('--collect',action='store_true');p.add_argument('--collect-attempt',type=int);p.add_argument('--collect-available',action='store_true');p.add_argument('--rerun-all',action='store_true');p.add_argument('--cancel-superseded',action='store_true');p.add_argument('--cancel-reason',default='Superseded by expanded mandatory release checks; no SDK source change.');a=p.parse_args()
    if a.collect_attempt:
        assert a.collect_attempt>0
        token=credential();base=f'actions/runs/{a.run_id}/attempts/{a.collect_attempt}'
        run=get(base,token);assert run['status']=='completed'
        jobs=get(base+'/jobs?per_page=100',token)
        # Never overwrite the latest attempt's top-level provenance snapshots.
        out=ROOT/'results/phase6/hosted'/str(a.run_id)/'attempt-history'/str(a.collect_attempt);out.mkdir(parents=True,exist_ok=True)
        (out/'run.json').write_text(json.dumps(run,indent=2)+'\n');(out/'jobs.json').write_text(json.dumps(jobs,indent=2)+'\n')
        archive=out/'workflow-logs.zip'
        if not archive.exists():download(base+'/logs',token,archive)
        unavailable=[]
        for job in jobs['jobs']:
            log=out/('job-'+str(job['id'])+'.txt')
            if log.exists():continue
            try:download(f"actions/jobs/{job['id']}/logs",token,log)
            except urllib.error.HTTPError as error:
                if error.code!=404:raise
                unavailable.append({'id':job['id'],'name':job['name'],'http_status':404,'conclusion':job['conclusion']})
        (out/'unavailable-job-log-blobs.json').write_text(json.dumps(unavailable,indent=2)+'\n')
        print('PRIOR_ATTEMPT_RETAINED',a.run_id,a.collect_attempt,'job_count',len(jobs['jobs']),'individual_blobs_unavailable',len(unavailable));return
    token=credential();run=get(f'actions/runs/{a.run_id}',token);jobs=get(f'actions/runs/{a.run_id}/jobs?per_page=100',token)
    artifacts=get(f'actions/runs/{a.run_id}/artifacts?per_page=100',token)
    out=ROOT/'results/phase6/hosted'/str(a.run_id);out.mkdir(parents=True,exist_ok=True)
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S')
    for name,value in [('run',run),('jobs',jobs),('artifacts',artifacts)]:
        (out/(stamp+'-'+name+'.json')).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    if a.rerun_all:
        assert run['head_branch']=='integration/multiversion-rc' and run['status']=='completed'
        req=request(f'actions/runs/{a.run_id}/rerun',token);req.method='POST'
        with urllib.request.urlopen(req,timeout=30) as response:code=response.status
        (out/(stamp+'-rerun.json')).write_text(json.dumps({'status':code,'from_attempt':run['run_attempt'],'sha':run['head_sha'],'scope':'Entire workflow fresh; no failed-jobs-only reuse. Previous evidence retained.'},indent=2)+'\n')
        print('FULL_RERUN_REQUESTED',a.run_id,code);return
    if a.cancel_superseded:
        assert run['head_branch']=='integration/multiversion-rc' and run['status']!='completed'
        req=request(f'actions/runs/{a.run_id}/cancel',token);req.method='POST'
        with urllib.request.urlopen(req,timeout=30) as response:code=response.status
        (out/(stamp+'-superseded.json')).write_text(json.dumps({'http_status':code,'reason':a.cancel_reason},indent=2)+'\n')
        print('SUPERSEDED_CANCEL_REQUESTED',a.run_id,code)
    counts={}
    for j in jobs['jobs']:
        key=j['conclusion'] or j['status'];counts[key]=counts.get(key,0)+1
    print(json.dumps({'id':a.run_id,'status':run['status'],'conclusion':run['conclusion'],'url':run['html_url'],'counts':counts,
        'active_or_failed':[{'id':j['id'],'name':j['name'],'status':j['status'],'conclusion':j['conclusion'],
                 'active_steps':[s['name'] for s in j.get('steps',[]) if s['status']=='in_progress'],
                 'failed_steps':[s['name'] for s in j.get('steps',[]) if s['conclusion']=='failure']}
                 for j in jobs['jobs'] if (j['status']=='in_progress' and counts.get('in_progress',0)<=3) or j['conclusion'] in ['failure','timed_out']]},indent=2))
    if a.collect or a.collect_available:
        if a.collect:assert run['status']=='completed','Collect only immutable completed run outputs'
        for artifact in artifacts['artifacts']:
            dest=out/(artifact['name']+'.zip')
            if not dest.exists():
                try:download(f"actions/artifacts/{artifact['id']}/zip",token,dest)
                except urllib.error.HTTPError as error:
                    if error.code!=404:raise
                    (out/(stamp+'-'+artifact['name']+'-unavailable.json')).write_text(json.dumps({'artifact':artifact,'http_status':404,'status':'NOT COLLECTED','note':'Metadata exists, blob temporarily absent or canceled upload incomplete; next collection retries. Not counted as evidence.'},indent=2)+'\n')
                    print('ARTIFACT_BLOB_NOT_COLLECTED',artifact['name']);continue
            extracted=out/artifact['name'];extracted.mkdir(exist_ok=True)
            receipt=extracted/'.extraction-complete.json'
            archive_hash=hashlib.sha256(dest.read_bytes()).hexdigest()
            expected={'artifact_id':artifact['id'],'archive_sha256':archive_hash,'run_id':a.run_id}
            if receipt.exists() and json.loads(receipt.read_text())==expected:continue
            with zipfile.ZipFile(dest) as z:
                assert all((extracted/n).resolve().is_relative_to(extracted.resolve()) for n in z.namelist())
                z.extractall(extracted)
            receipt.write_text(json.dumps(expected,indent=2)+'\n')
        if run['status']=='completed':
            logs=out/('workflow-attempt-'+str(run['run_attempt'])+'-logs.zip')
            if not logs.exists():download(f'actions/runs/{a.run_id}/logs',token,logs)
        for job in jobs['jobs']:
            if job['status']!='completed':continue
            log=out/('job-'+str(job['id'])+'.txt')
            if not log.exists():
                try:download(f"actions/jobs/{job['id']}/logs",token,log)
                except urllib.error.HTTPError as error:
                    if error.code!=404:raise
                    (out/('job-'+str(job['id'])+'-log-unavailable.json')).write_text(json.dumps({'job':job['id'],'status':job['status'],'conclusion':job['conclusion'],'http_status':404,'note':'GitHub has no log blob, e.g. canceled job that never started. Workflow logs and artifacts retained separately.'},indent=2)+'\n')

if __name__=='__main__':main()
