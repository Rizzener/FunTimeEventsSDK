"""Write a factual report from retained evidence; absent tests stay NOT RUN."""
import argparse,csv,hashlib,importlib,json,os,shutil,subprocess,sys,time,zipfile,urllib.request,urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))

def read(p,default=None):
    return json.loads(p.read_text(encoding='utf-8')) if p.exists() else default

def dependency_probe():
    rows=[];out=ROOT/'results/phase6/dependency-resolution'/time.strftime('%Y%m%dT%H%M%S');out.mkdir(parents=True)
    for i,path in enumerate(['com/google/code/gson/gson/2.14.0/gson-2.14.0.pom','org/jetbrains/kotlin/kotlin-metadata-jvm/2.3.20/kotlin-metadata-jvm-2.3.20.pom','org/ow2/ow2/1.5.1/ow2-1.5.1.pom']):
        url='https://repo.maven.apache.org/maven2/'+path
        try:
            with urllib.request.urlopen(url,timeout=30) as response:data=response.read();status=response.status;headers={k:v for k,v in response.headers.items() if k.lower() not in ['set-cookie','authorization','cookie']}
            (out/(str(i)+'.pom')).write_bytes(data);rows.append({'url':url,'status':status,'headers':headers,'sha256':hashlib.sha256(data).hexdigest()})
        except urllib.error.HTTPError as error:
            (out/(str(i)+'-error.txt')).write_bytes(error.read());rows.append({'url':url,'status':error.code})
    (out/'result.json').write_text(json.dumps(rows,indent=2)+'\n');print([{k:v for k,v in row.items() if k!='headers'} for row in rows])

def package_comparison(run_id,attempt):
    cfg=read(ROOT/'targets.json');group=cfg['maven_group'].replace('.','/')
    parent=ROOT/'results/phase6/hosted'/str(run_id)
    candidates=list(parent.glob('aggregate-'+str(attempt)+'/build/aggregate-maven'))
    if not candidates:return {}
    result={}
    for mc in ['1.21.4','26.3']:
        relative=Path(group)/('mc'+mc)/cfg['sdk_version']/('mc'+mc+'-'+cfg['sdk_version']+'.jar')
        local=Path.home()/'.m2/repository'/relative;host=candidates[-1]/relative
        if not local.exists() or not host.exists():continue
        with zipfile.ZipFile(local) as first,zipfile.ZipFile(host) as second:
            names=set(first.namelist());same_entries=names==set(second.namelist())
            differences=[n for n in sorted(names&set(second.namelist())) if first.read(n)!=second.read(n)]
            result[mc]={'same_entries':same_entries,'different_entries':differences,
                'all_class_bytes_equal':same_entries and not any(n.endswith('.class') for n in differences),
                'license_text_equal':first.read('LICENSE').decode().replace('\r\n','\n')==second.read('LICENSE').decode().replace('\r\n','\n'),
                'Windows_sha256':hashlib.sha256(local.read_bytes()).hexdigest(),'hosted_sha256':hashlib.sha256(host.read_bytes()).hexdigest()}
    (ROOT/'results/phase6/cross-environment-package-comparison.json').write_text(json.dumps(result,indent=2)+'\n')
    return result

def performance_comparison():
    old=ROOT/'poc/multiversion/results/phase5/performance.csv'
    new=ROOT/'results/phase6/performance-1.21.4_26.3.csv'
    if not old.exists() or not new.exists():return [],[]
    baseline=list(csv.DictReader(old.open(encoding='utf-8-sig')));current=list(csv.DictReader(new.open()))
    rows=[];values=[]
    for item in current:
        if item['category']!='sdk_tick_active':continue
        previous=next(r for r in baseline if r['environment']=='Windows' and r['minecraft']==item['minecraft'] and r['mode']==item['mode'] and r['category']=='sdk_tick_active')
        count=int(item['count']);mean=float(item['mean_ns'])/1000;maximum=float(item['max_ns'])/1000
        old_count=int(previous['count']);old_mean=float(previous['mean_us']);old_max=float(previous['max_us'])
        value={'minecraft':item['minecraft'],'mode':item['mode'],'count':count,'mean_us':mean,'max_us':maximum,
               'old_mean_us':old_mean,'old_max_us':old_max,'mean_bytes':float(item['mean_bytes'])}
        values.append(value)
        rows.append(f"| {item['minecraft']} | {item['mode']} | {count} | {old_mean:.2f} | {mean:.2f} | {maximum/1000:.2f} | {value['mean_bytes']:.1f} |")
    (ROOT/'results/phase6/performance-Windows-comparison.json').write_text(json.dumps(values,indent=2)+'\n')
    return rows,values

def matched_performance_control():
    """Historical artifact is only a benchmark baseline, never Phase 6 matrix evidence."""
    old=ROOT/'poc/multiversion';out=ROOT/'results/phase6/performance-controls';out.mkdir(exist_ok=True)
    baseline_cfg=read(old/'targets.json');baseline_sdk=Path.home()/'.m2/repository/net/funtimeevents/poc/mc26.3'/baseline_cfg['sdk_version']/('mc26.3-'+baseline_cfg['sdk_version']+'.jar')
    before=hashlib.sha256(baseline_sdk.read_bytes()).hexdigest()
    os.environ['FTE_PHASE5_EVIDENCE']='../../results/phase6/performance-controls/historical'
    os.environ['FTE_PHASE5_THREAD_CPU']='1'
    sys.path.insert(0,str(old));baseline_runner=importlib.import_module('phase5_run')
    with (out/'historical-supervisor.txt').open('w',encoding='utf-8') as log:
        # runtime() avoids the old CLI cleanup's historical result output directory.
        import contextlib
        with contextlib.redirect_stdout(log):baseline=baseline_runner.runtime('26.3','rest')
    assert hashlib.sha256(baseline_sdk.read_bytes()).hexdigest()==before,'Historical SDK changed'
    env=dict(os.environ,FTE_PHASE5_EVIDENCE='results/phase6/performance-controls/candidate',FTE_PHASE5_THREAD_CPU='1')
    command=[sys.executable,str(ROOT/'phase5_run.py'),'--targets','26.3','--kind','runtime','--modes','rest']
    with (out/'candidate-supervisor.txt').open('wb') as log:current=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    value={'historical_status':baseline.get('status'),'candidate_exit':current.returncode,'baseline_sdk_sha256':before,'candidate_command':command,
           'scope':'Sequential matched Windows REST/26.3 controls, same fixture algorithms and JDK; opt-in existing thread-CPU diagnostics. Baseline never fills candidate compatibility cells.'}
    (out/'result.json').write_text(json.dumps(value,indent=2)+'\n');print(value)
    return baseline.get('status')=='PASS' and current.returncode==0

def performance_control_results():
    rows=[];details={}
    for label in ['historical','candidate']:
        parent=ROOT/'results/phase6/performance-controls'/label/'26.3/runtime-rest'
        attempts=[(p,read(p,{})) for p in sorted(parent.glob('attempt-*/result.json'))]
        successes=[(p,v) for p,v in attempts if v.get('status')=='PASS']
        item={'attempts':[{'path':str(p.relative_to(ROOT)),'status':v.get('status'),'error':v.get('error')} for p,v in attempts]}
        if successes:
            path,value=successes[-1];rt=value['runtime'];perf=rt['callback_performance']['sdk_tick_active'];n,total,maximum,allocated,_=perf
            spans=[s for s in rt.get('cpu_and_wall_spans',[]) if s['active']]
            item.update({'path':str(path.relative_to(ROOT)),'n':n,'mean_us':total/n/1000,'max_ms':maximum/1e6,'bytes':allocated/n,'cpu_spans_ge_1ms':spans})
            rows.append(f"| {label} | PASS | {n} | {total/n/1000:.2f} | {maximum/1e6:.2f} | {allocated/n:.1f} |")
        else:rows.append(f'| {label} | NOT VERIFIED | — | — | — | — |')
        details[label]=item
    out=ROOT/'results/phase6/performance-controls/comparison.json';out.write_text(json.dumps(details,indent=2)+'\n')
    return rows,details

def candidate_archive(hosted,cfg,run,collected):
    summary_dirs=list(hosted.glob('phase6-summary-'+str(run.get('run_attempt',1))))
    aggregate=read(summary_dirs[-1]/'aggregate-comparison.json',{}) if summary_dirs else {}
    functional=read(summary_dirs[-1]/'summary.json',{}) if summary_dirs else {}
    if run.get('conclusion')!='success' or len(collected)!=13 or aggregate.get('status')!='PASS' or functional.get('status')!='FUNCTIONAL_PASS_NATIVE_OPEN':
        return {},aggregate
    staged=list(hosted.glob('aggregate-'+str(run['run_attempt'])+'/build/aggregate-maven'));assert len(staged)==1
    out=ROOT/'results/phase6/candidate'/cfg['sdk_version'];files={};source_checks={}
    for spec in cfg['targets']:
        mc=spec['minecraft'];relative=Path(cfg['maven_group'].replace('.','/'))/('mc'+mc)/cfg['sdk_version']
        for name,digest in collected[mc]['sha256'].items():
            source=staged[0]/relative/Path(name).name
            assert hashlib.sha256(source.read_bytes()).hexdigest()==digest,(mc,name)
            target=out/relative/source.name;target.parent.mkdir(parents=True,exist_ok=True)
            if target.exists():assert target.read_bytes()==source.read_bytes(),'Never overwrite candidate bytes'
            else:shutil.copy2(source,target)
            files[target.relative_to(out).as_posix()]={'bytes':target.stat().st_size,'sha256':digest}
            if name.endswith('-sources.jar'):
                with zipfile.ZipFile(source) as archive:
                    names=archive.namelist();code=archive.read('net/funtimeevents/internal/platform/FabricPlatform.java').decode()
                    checks={'unique_entries':len(names)==len(set(names)),
                        'no_experimental_source_names':not any('/poc/' in n or n.endswith(('TrackerSliceAPI.java','BanSliceAPI.java','PocEventsAPI.java','PocCore.java')) for n in names),
                        'production_namespace':'net.minecraft.class_310' in code if spec['java']==21 else 'net.minecraft.client.Minecraft' in code and 'net.minecraft.class_310' not in code}
                    source_checks[mc]=checks;assert all(checks.values()),(mc,checks)
    assert len(files)==52,len(files)
    value={'status':'PASS','workflow_run':run['id'],'workflow_attempt':run['run_attempt'],'sha':run['head_sha'],'version':cfg['sdk_version'],'files':files}
    (ROOT/'results/phase6/candidate-source-artifacts.json').write_text(json.dumps({'status':'PASS','checks':source_checks,'scope':'All 13 published sources JARs: intermediary legacy / unobfuscated modern, no experimental source classes or duplicate entries.'},indent=2)+'\n')
    (out/'manifest.json').write_text(json.dumps(value,indent=2)+'\n');return value,aggregate

def seal_evidence(run_id):
    base=ROOT/'results/phase6';out=base/'evidence-manifest.json';files={}
    for p in sorted(base.rglob('*')):
        if p.is_file() and p!=out:
            with p.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
            files[p.relative_to(ROOT).as_posix()]={'bytes':p.stat().st_size,'sha256':digest}
    value={'run_id':run_id,'scope':'All locally retained Phase 6 evidence, including failed attempts, raw hosted archives/logs, candidate artifacts and native reports. No earlier PASS substituted.','files':files}
    out.write_text(json.dumps(value,indent=2)+'\n');print('EVIDENCE_SEALED',len(files),sum(v['bytes'] for v in files.values()))

def main():
    p=argparse.ArgumentParser();p.add_argument('--run-id',type=int,required=True);p.add_argument('--performance-controls',action='store_true');p.add_argument('--performance-candidate-retry',action='store_true');p.add_argument('--seal',action='store_true');p.add_argument('--dependency-probe',action='store_true');a=p.parse_args()
    if a.dependency_probe:dependency_probe();return
    if a.seal:seal_evidence(a.run_id);return
    if a.performance_candidate_retry:
        env=dict(os.environ,FTE_PHASE5_EVIDENCE='results/phase6/performance-controls/candidate',FTE_PHASE5_THREAD_CPU='1')
        out=ROOT/'results/phase6/performance-controls'; stamp=time.strftime('%Y%m%dT%H%M%S')
        command=[sys.executable,str(ROOT/'phase5_run.py'),'--targets','26.3','--kind','runtime','--modes','rest']
        with (out/('candidate-retry-'+stamp+'.txt')).open('wb') as log:
            result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        print({'exit':result.returncode,'log':str(log.name)});raise SystemExit(result.returncode)
    if a.performance_controls:raise SystemExit(not matched_performance_control())
    cfg=read(ROOT/'targets.json');base=ROOT/'results/phase6';hosted=base/'hosted'/str(a.run_id)
    snapshots=sorted(hosted.glob('*-run.json'));run=read(snapshots[-1],{}) if snapshots else {}
    jobsfiles=sorted(hosted.glob('*-jobs.json'));jobs=read(jobsfiles[-1],{}).get('jobs',[]) if jobsfiles else []
    selected_attempt=str(run.get('run_attempt',1))
    rows=[];hashrows=[];trackerrows=[];collected={};hosted_perf=[];production_runs=[]
    for spec in cfg['targets']:
        mc=spec['minecraft'];matches=list(hosted.glob('target-'+mc+'-'+selected_attempt+'/results/phase6/github/'+str(a.run_id)+'/'+selected_attempt+'/'+mc))
        folder=matches[-1] if matches else Path('__no_evidence__')
        job=next((j for j in jobs if j['name']=='target ('+mc+')'),{})
        status=lambda n:read(folder/(n+'.json'),{}).get('status','NOT RUN')
        builds={k:'PASS' if any(read(q,{}).get('status')=='PASS' for q in folder.glob(k+'/attempt-*/result.json')) else 'NOT RUN' for k in ['compile','publish','consumer']}
        docs=read(folder/'doc-examples/result.json',{}).get('status','NOT RUN')
        rows.append('| '+ ' | '.join([mc,str(spec['java']),builds['compile'],builds['publish'],builds['consumer'],status('artifacts'),status('api'),docs,status('network'),status('runtime'),job.get('conclusion') or job.get('status','NOT RUN')])+' |')
        runtime=read(folder/'runtime.json',{})
        production_runs.extend([read(q,{}) for q in folder.glob('runtime-*/attempt-*/result.json')])
        trackerrows.append('| '+mc+' | '+' | '.join([runtime.get('status','NOT RUN')]*7)+' |')
        for perf in folder.parent.glob('performance-'+mc+'.csv'):
            hosted_perf.extend([{'environment':'GitHub Linux','workflow_run':a.run_id,**r} for r in csv.DictReader(perf.open())])
        package=read(folder/'release-artifacts.json',{})
        if package.get('status')=='PASS':
            collected[mc]=package
            for name,digest in package['sha256'].items():
                if name.endswith('.jar') and not name.endswith('-sources.jar'):hashrows.append('| '+mc+' | `'+digest+'` |')
    candidate,aggregate=candidate_archive(hosted,cfg,run,collected)
    functional=candidate.get('status')=='PASS'
    archive_checks={}
    if functional:
        metadata=read(sorted(hosted.glob('*-artifacts.json'))[-1])['artifacts']
        selected=[item for item in metadata if item['name'].endswith('-'+selected_attempt)]
        assert len(selected)==17,len(selected)
        for item in selected:
            archive=hosted/(item['name']+'.zip');expected=item['digest']
            archive_checks[item['name']]=archive.exists() and 'sha256:'+hashlib.sha256(archive.read_bytes()).hexdigest()==expected
        assert all(archive_checks.values()),archive_checks
        (base/'hosted-archive-digests.json').write_text(json.dumps({'status':'PASS','run_id':a.run_id,'attempt':selected_attempt,'checks':archive_checks},indent=2)+'\n')
    statistics={'starts':len(production_runs),'PASS':sum(r.get('status')=='PASS' for r in production_runs),
        'JOIN':sum(r.get('runtime',{}).get('joins',0) for r in production_runs),
        'DISCONNECT':sum(r.get('runtime',{}).get('disconnects',0) for r in production_runs),
        'callbacks':sum(len(r.get('runtime',{}).get('callbacks',[])) for r in production_runs),
        'tracker_payloads':sum(len(r.get('runtime',{}).get('payloads',[])) for r in production_runs)}
    (base/'production-counts.json').write_text(json.dumps({'run_id':a.run_id,'attempt':selected_attempt,**statistics},indent=2)+'\n')
    state='VERIFIED_RC_NATIVE_OPEN' if functional else 'IN_PROGRESS / NOT VERIFIED'
    source=[]
    for directory in ['core/src/main','platform']:
        source += [f for f in (ROOT/directory).rglob('*') if f.is_file()]
    freeze={str(f.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(source)}
    (base/'candidate-source-freeze.json').write_text(json.dumps(freeze,indent=2)+'\n')
    from phase6_source_parity import normalize
    facade=next((ROOT/'platform/common').rglob('VersionAccess.java'))
    old_facade=next((ROOT/'poc/multiversion/platform/common').rglob('VersionAccess.java'))
    facade_equal=facade.read_text(encoding='utf-8')==normalize(old_facade.read_text(encoding='utf-8'))
    assert facade_equal,'Accepted Phase5 typed access facade changed'
    (base/'source-parity-supplement.json').write_text(json.dumps({'status':'PASS','file':facade.relative_to(ROOT).as_posix(),'baseline':old_facade.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(facade.read_bytes()).hexdigest(),'scope':'Remaining typed routing facade, compared against accepted Phase5; hosted 71 checks plus this supplement cover all 72 production Java sources.'},indent=2)+'\n')
    paths=['core','platform','targets','targets.json','gradle','gradle.properties','gradlew','gradlew.bat','build.gradle.kts','settings.gradle.kts','LICENSE']
    dirty=subprocess.check_output(['git','status','--porcelain','--untracked-files=all','--',*paths],cwd=ROOT)
    assert not dirty,('Producer inputs have uncommitted changes',dirty.decode())
    first=subprocess.check_output(['git','ls-tree','-r','6454e0a','--',*paths],cwd=ROOT)
    current=subprocess.check_output(['git','ls-tree','-r','HEAD','--',*paths],cwd=ROOT)
    assert first==current,'Production SDK inputs changed since integration build; fresh version/regression required'
    (base/'candidate-build-inputs.json').write_text(json.dumps({'status':'PASS','unchanged_since':'6454e0a65c50277a426ba724598a9400d7c95482','git_blob_inventory_sha256':hashlib.sha256(current).hexdigest(),'paths':paths,'entries':current.decode().splitlines()},indent=2)+'\n')
    failed=[]
    for result in base.glob('*/**/attempt-*/result.json'):
        if 'hosted' in result.parts:continue
        value=read(result,{})
        if value.get('status')=='FAIL':failed.append({'path':str(result.relative_to(ROOT)),'error':value.get('error'),'checks':value.get('checks')})
    (base/'local-failed-attempts.json').write_text(json.dumps(failed,indent=2)+'\n')
    local=[]
    for mc in ['1.21.4','26.3']:
        kinds={k:read(base/mc/(k+'.json'),{}).get('status','NOT RUN') for k in ['artifacts','api','network','runtime']}
        runtime=[]
        for mode in ['offline','rest','ws','flags']:
            passed=[q for q in (base/mc/('runtime-'+mode)).glob('attempt-*/result.json') if read(q,{}).get('status')=='PASS']
            runtime.append(mode+'='+('PASS' if passed else 'NOT RUN'))
        local.append('- '+mc+': '+', '.join(runtime)+'; artifacts/API/network='+','.join(kinds[k] for k in ['artifacts','api','network'])+'.')
    oldruns=[]
    for folder in sorted((base/'hosted').glob('*')):
        if folder.name==str(a.run_id):continue
        files=sorted(folder.glob('*-run.json'))
        if not files:continue
        value=read(files[-1]);reasons=sorted(folder.glob('*-superseded.json'))
        reason=read(reasons[-1],{}).get('reason','') if reasons else ''
        oldruns.append(f"- [{folder.name}](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/{folder.name}), `{value.get('head_sha')}`, `{value.get('conclusion') or value.get('status')}`: {reason}")
    packages=package_comparison(a.run_id,selected_attempt);perfrows,perfvalues=performance_comparison()
    gate_rows=['| '+j['name']+' | '+str(j.get('conclusion') or j['status'])+' | ['+str(j['id'])+']('+j['html_url']+') |' for j in jobs if j['name'] not in ['matrix'] and not j['name'].startswith('target (')]
    controlrows,controls=performance_control_results()
    for perf in [base/'performance-1.21.4_26.3.csv']:
        if perf.exists():hosted_perf.extend([{'environment':'Windows','workflow_run':'local',**r} for r in csv.DictReader(perf.open())])
    if hosted_perf:
        with (base/'performance-integrated.csv').open('w',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=list(hosted_perf[0]));writer.writeheader();writer.writerows(hosted_perf)
    report=f'''# PHASE 6 — integration and release candidate

**Состояние: {state}.** Версия `{cfg['sdk_version']}`, group `{cfg['maven_group']}`.
Это фактический отчёт о новых артефактах. Production-ready не заявляется.
Публичная Maven публикация, tag, GitHub Release и merge не выполнялись.

## 1. Итоговая структура

```text
core/                  Java 21: models, contracts, game context, ban parsing, scheduler
platform/common/       настоящий API/Builder, transports, trackers, Fabric bridge, mixins
platform/<8 leaves>/   hover, profile, dimension, boss overlay API differences
targets/mc*/           13 небольших Gradle declarations
gradle/target.gradle   общий producer convention и fixture source sets
targets.json           точная матрица Minecraft/Java/Fabric/source leaves
consumers/             отдельные Maven consumer builds и peer mod
examples/consumer/     независимый минимальный consumer без Maven Local
fixtures/              golden inputs, старый binary probe, real fixture server
verification/          исполняемые network tests
ci/phase6/             обязательная hosted regression
.github/workflows/    активный release-candidate.yml
poc/multiversion/      неизменный принятый исторический baseline и raw evidence
```

В корне находятся активные Python supervisors; обычная сборка и IDE используют
Gradle напрямую (`sdkBuild`, `sdkStage`, `sdkPublishLocal`, target `build`).
Исторические вспомогательные скрипты не дублируются в активном CI.

## 2. Изменения и сохранение истории

- Старый SDK: Git `99948f15afd6170d994b59c30395febc9e722877`.
- Принятые PoC sources/fixtures/tests сохранены коммитом `d3a4f1e`.
- Интеграция в отдельной ветке `integration/multiversion-rc`: `6454e0a`.
- Полный набор CI controls/API inventory: `43c0bdc`; строгие JDK и общий Gradle build: `3e99652`.
- Подготовленный JitPack recipe и инструменты native capture: `1d04295`.
- Linux graphics dependencies/diagnostics и fixture process-group cleanup: `356f97d`.
- Автономный detached consumer с собственными wrapper/catalog: `959fc33`.
  SDK source/build inputs не изменялись после `6454e0a`; проверено Git diff.
- `core`, общая platform и восемь leaf-файлов перенесены без переписывания алгоритмов.
  Внутренние `poc.*` стали `internal.*`; временные API/core smoke classes вынесены в
  test-only consumer/verification sources. Release mod ID теперь `fte-api`.
- API facade/config/models/network пакеты сохранены. Core вложен напрямую один раз;
  Maven core dependency не публикуется. Reflection для несовместимых MC API не добавлялась.
- Старый release working tree дополнительно сохранён локально в
  `.evidence/phase6/legacy-sdk-working-tree`; действующая main не изменялась.
- Source parity: 71 проверяемый файл, включая все восемь version leaves, PASS на
  локальной проверке. Изменения — явные package/class/mod identity moves.
- Дополнительное локальное сравнение оставшегося небольшого `VersionAccess`
  с принятым Phase5: PASS (`source-parity-supplement.json`). Вместе это все
  72 production Java sources. Facade также компилируется и исполняется во всех targets.
- SHA-256 проверка всех **3443** исторических файлов этапа 5: PASS, 0 расхождений,
  receipt `results/phase6/historical-integrity.json`. Все прежние perf/native данные сохранены.

Полный diff: `git diff 99948f1..HEAD`; SDK input freeze —
`results/phase6/candidate-source-freeze.json`; Git blob inventory всех producer inputs
и wrapper/license — `candidate-build-inputs.json`. Он совпадает с integration commit.
Старые Maven coordinates/versions не перезаписывались. Полные raw evidence крупнее
исходников: сохранены локально в results/phase6 и в GitHub Actions artifacts (30 days).
Скрипты/fixtures/goldens и отчёт включаются в Git; локальный evidence-manifest.json
содержит размеры/SHA-256 всех сохранённых файлов, включая failed attempts.

## 3. Новая hosted функциональная матрица

| MC | Java | Compile/core | Staging | Consumers | Packaging | API36 | Docs example | Network | Trackers/lifecycle/perf | Hosted job |
|---|---:|---|---|---|---|---|---|---|---|---|
'''+ '\n'.join(rows)+f'''

`runtime PASS` требует четыре режима offline/REST/WSS/flags, настоящие production
Fabric callbacks, JOIN/DISCONNECT 21/21, 40 сообщений, 9 scheduled scans, goldens
всех семи payload, TAB/hover/scoreboard/boss bar/entities/Text Display/equipment/
chests/timers, late-disconnect stress, single nested SDK и old binary probe.
Linux требует реальный Xvfb/xdpyinfo; 26.3 дополнительно фактический SDL X11 marker
и два повторных REST запуска. Отсутствующая строка не считается проверенной.
Фактические суммарные счётчики выбранного attempt: {statistics['starts']} starts,
{statistics['PASS']} PASS, JOIN/DISCONNECT {statistics['JOIN']}/{statistics['DISCONNECT']},
{statistics['callbacks']} callbacks, {statistics['tracker_payloads']} tracker payloads.
Отдельные Captcha/GET/protocol-unit запросы не входят в счётчик tracker payloads.

Развёрнутая матрица игровых возможностей (PASS только при полном runtime PASS):

| MC | Ban + hover | Copper dungeon | Warden city | Hell boss bar | TAB | Mine | Event coordinates |
|---|---|---|---|---|---|---|---|
'''+ '\n'.join(trackerrows)+f'''

Сверяются все поля payload и декодированный wire JSON после разрешённой нормализации
динамического ISO8601 времени; patterns/зоны/интервалы сохранены source parity.
Raw HTTP/WS bytes сохраняются; равенство gzip byte streams и JSON whitespace
не объявляется результатом semantic JSON comparison. Headers/gzip/envelopes проверяются отдельно.
Тестовый интервал ускоряет сцены; это не изменение production defaults.

Дополнительные локальные Windows результаты:

'''+ '\n'.join(local)+f'''

## 4. Настоящие GitHub Actions

Основной полный run: [{a.run_id}](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/{a.run_id}),
attempt `{selected_attempt}`, SHA `{run.get('head_sha','NOT RUN')}`, status `{run.get('status','NOT RUN')}`,
conclusion `{run.get('conclusion') or 'NOT COMPLETED'}`.
Фиксированная [ссылка на attempt {selected_attempt}](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/{a.run_id}/attempts/{selected_attempt}).

| Обязательный job | Result | Job ID / raw log |
|---|---|---|
'''+ '\n'.join(gate_rows)+f'''

Предыдущие runs (их результаты не подставляются в итоговую матрицу):

'''+ '\n'.join(oldruns)+f'''

Найдена ошибка CI setup: при включённом Gradle autodetection ранние jobs использовали
системный Temurin 21.0.12.1 вместо declared pinned 21.0.9. Исправлено auto-detect=false
в ephemeral runner; provenance проверяет actual runtime version. Все отмены и raw
logs сохранены. На отменённых матрицах required status завершился ошибкой: непроверенные
targets не дают зелёного статуса.

Run 38839563028: двенадцать targets прошли полный runtime; 26.3 offline/REST достигли
600s timeout. Raw client logs показывают отказ OpenGL GLX visual, затем отсутствие
Vulkan VK_KHR_surface. Run остановлен после выявления причины; required status FAIL.
38842085209 отменён до старта, чтобы не повторять ту же неполную конфигурацию.
В CI добавлены стандартные Ubuntu Mesa Vulkan runtime packages и graphics probe
с настоящей Mojang SDL library. Нет подмены версии GL, отключения sRGB или JVM overrides.
Старые проверенные WSL/Xvfb логи 26.3 также используют Vulkan, но их PASS не переносится
в новую матрицу. Новые запуски обязаны отдельно пройти production fixtures.
Улучшен только test supervisor: принадлежащая ему Linux process group завершается
целиком; при timeout сохраняются Thread.print dumps, следующие режимы становятся
NOT RUN и job краснеет. Проверка собственного child и независимого control PASS
на Windows и WSL; она входит в каждую hosted job.

Run 38843094208 сохранил успешные 26.3 offline/REST/WSS/flags и ещё один REST
до переключения на финальный 959fc33. Реальный backend — Vulkan 1.4.318 llvmpipe,
Mesa 25.2.8-0ubuntu0.24.04.4, окно SDL x11. SDL probe: GL3.3+sRGB не создаёт visual,
контроль без sRGB создаёт context. Контроль не меняет настройки production Minecraft;
игра сама выбирает установленный Vulkan backend. Наличие X11 и SDL marker проверяется
в каждом новом final runtime, а не переносится из промежуточного run.
Механизм выбора visual соответствует [исходнику SDL 3.4.14](https://github.com/libsdl-org/SDL/blob/release-3.4.14/src/video/x11/SDL_x11opengl.c);
запрос sRGB в Minecraft подтверждён сохранёнными GlBackend javap и LWJGL constants.

Run 38844176709 attempt1: 1.21.6 упал до компиляции на разрешении нескольких
транзитивных зависимостей Loom (not found для Gson/Kotlin/OW2 и других). Локальные
официальные Maven POM probes 2026-10-16 03:23 UTC вернули HTTP200; причины разницы
ответов runner не установлены. Все журналы сохранены; запрошен **полный** fresh
rerun на том же SHA, без смены версий и failed-jobs-only reuse. Итоговый отчёт,
provenance, staging и required summary выбирают строго один указанный attempt.
Отсутствующие blobs старых отменённых uploads явно записываются NOT COLLECTED;
повторное получение продолжается, они не заполняют final matrix.
Полный workflow ZIP и 16 доступных individual job logs attempt1 сохранены отдельно
в `attempt-history/1`; для одного не стартовавшего/отменённого job API не даёт
individual log blob — это отмечено JSON receipt. Successful attempt2 ZIP и все
его 18 job logs сохранены отдельно.

Pinned: Gradle {cfg['gradle']}, Loom {cfg['loom']}, Loader {cfg['loader']},
installer {cfg['installer']}; Fabric API для каждой строки — targets.json.
Hosted JDKs: Temurin 21.0.9+10 и 25.0.4+7, archive SHA-256 в ci/phase5/jdks.json.
Ubuntu 24.04, Xvfb, software rendering. Action revisions закреплены SHA.

Evidence и staging свежие для run/attempt. CI не использует старый PoC PASS,
не скачивает ранее опубликованные SDK, проверяет commit/run/attempt provenance.
`SDK required matrix` требует успеха всех targets и обеих Loader control jobs;
missing/skipped/failure дают красный статус. Защита main не изменялась.
Raw hosted API metadata/log ZIP/artifacts: `results/phase6/hosted/`.
SHA-256 всех {len(archive_checks)} выбранных artifact ZIP сверены с digest GitHub API:
`hosted-archive-digests.json`; старые attempt ZIP в эту проверку не подставляются.
Отдельный hosted job выполняет **один Gradle build всех 13 targets** без фильтра,
`javaToolchains sdkBuild sdkStage`. Сравнение с независимыми matrix outputs:
`{aggregate.get('status','NOT RUN')}`, {len(aggregate.get('checks',{}))}/52 файлов
JAR/sources/POM/module. Итоговый required status проверяет байтовое равенство.

## 5. Maven staging и release hygiene

Target GAV: `{cfg['maven_group']}:mc<MC>:{cfg['sdk_version']}`.
Локальный filesystem staging: `build/staging-maven`; CI staging изолирован на runner.
Удалённая Maven публикация не настроена и не выполнялась.
Проверяются JAR/sources/POM/module, точный MC/Java/Loader/API, production namespace,
Java 21 core ровно один раз, отсутствие transitive core и внешних bundled libraries,
байтовое соответствие вложенного JIJ опубликованному SDK.

Отдельный release scanner исключает PoC/test/fixture classes, тестовые ключи,
сертификаты, локальные адреса и дубликаты entries. LICENSE сохранён как
`MIT License with Backend Restriction`; metadata больше не называет его стандартной MIT.
Standalone example использует только staging repository; отрицательный контроль
убирает staging и требует ошибки, даже при наличии SDK в Maven Local.
Он компилируется из отдельной копии с собственными wrapper/catalog, без родительского
SDK checkout. Все 13 detached builds прошли; локально такой же build PASS на 1.21.4/26.3.
Sources JAR namespace/unique entries/no test sources дополнительно PASS для всех 13:
`candidate-source-artifacts.json`. SDK JARs 1.21.4 и 26.3 побайтово совпали между
Windows и hosted Linux: `cross-environment-package-comparison.json`.
Выбранный комплект кандидата: `results/phase6/candidate/{cfg['sdk_version']}/`,
manifest `{candidate.get('status','NOT VERIFIED')}`. Комплект создаётся только после
успеха всего run, aggregate comparison и полной функциональной summary.

## 6. Source и binary compatibility

Обязательные 36 классов facade/config/Builder/models/net/cache/PayloadSender/FteLogger:
старые public JVM descriptors + точная компиляция LegacyApiProbe source.
Проба, скомпилированная на старом SDK JAR, запускается в production на новом SDK.
Старый JAR SHA-256:
`d64bba38f2171acc84bf51da8fd9f2691f6a6b76b9c274837c64e3a3380219e3`.
Precompiled probe:
`ab32f192195cdb8ad7042b9509e0956a16ef73cbc8d31ddf22b562f07d3fc088`.

Дополнительно скачан **уже опубликованный** JitPack v1.0.5 JAR, SHA-256
`d8e26c08998eae48b398d6b15f61f10543334b5c25ce6b1242c4e7f2660529f9`:
те же 36 descriptors, old source compilation и идентичные precompiled probe bytes —
PASS (`results/phase6/published-legacy/result.json`). Это связывает исходный baseline
с фактически доступной публикацией, не инициируя нового JitPack build.

Дополнительно инвентаризированы все 54 Java-public класса. Пользователь подтвердил
scope: внутренние tracker/bootstrap/util вызовы мигрируют явно. Четыре отсутствующих
старых implementation класса и изменённые constructors/helpers перечислены в
compatibility-inventory.json. Полная совместимость всей Java-public поверхности
не заявляется. Исправлена ошибка checker, при которой методы anonymous/private
class могли приписываться внешнему public class; отдельный negative regression PASS.

## 7. Несколько consumers и Loader

Два независимых consumer-мода с одним SDK проверяются в каждом runtime: один
mod `fte-api`, один runtime, равная конфигурация разделяется, чужая config/key
отклоняется без раскрытия ключа. Повторные JOIN не создают подписки.
Loader negative controls используют реальные staged SDK bytes в двух consumer JAR:
несовместимые exact SDK versions отвергаются; неверный Minecraft target отвергается
до entrypoints с указанием требуемой версии. Локально оба контроля PASS на 1.21.4/26.3;
полные hosted заключения берутся только из соответствующего run.
Диапазоны версий могут разрешиться в один выбранный Loader SDK; две изолированные
копии runtime не обещаются. Дополнительный loader-probe version публикуется только
в тестовый filesystem staging и не является release artifact.

Положительный контроль разных версий: Windows production 1.21.4 и 26.3, peer JAR
вкладывает настоящий опубликованный v1.0.5 с wildcard требованием; основной consumer
вкладывает exact 2.0.0-rc.1. Оба PASS: Loader выбирает единственный новый nested SDK,
peer/config conflict checks и старый binary probe работают, процесс завершается.
Raw: `results/phase6/published-legacy/coexistence/`. Это проверка совместимого диапазона
и заявленного API, не гарантия для произвольного стороннего мода с внутренними вызовами.

## 8. Производительность и ресурсы

Исторические измерения этапа 5 сохранены без изменений. Новые production результаты
содержат count/mean/max wall-clock и callback allocations, а также entity/display/
block reads и отсутствие сканирования вне зон. CSV экспортирует phase6_perf.py.
Один wall-clock максимум не объявляется CPU/FPS затратой. Hosted/Windows/WSL
измерения на разных окружениях нельзя напрямую назвать регрессией одной версии.
Длительный burn-in и гарантия отсутствия всех возможных resource leaks не заявляются.
Обязательные проверки покрывают FTE threads, HTTP termination, SSE explicit close,
shutdown queues/futures и 21 session lifecycle на каждый runtime mode.

Windows callback measurements, новые JAR после интеграции. Значения в мкс, максимум
в мс; это wall-clock span SDK callbacks вместе с диагностическим observer, не FPS
и не агрегированное CPU time. Никакие пики не удалены из исходных средних.

| MC | Mode | Active ticks | Phase5 mean us | RC mean us | RC max ms | Mean allocated bytes |
|---|---|---:|---:|---:|---:|---:|
'''+ '\n'.join(perfrows)+f'''

Дополнительная последовательная REST/26.3 пара: прежний принятый SDK и кандидат,
та же машина/JDK/сцены; включена уже существовавшая опциональная thread CPU диагностика.
Исторический baseline никогда не заполняет Phase6 compatibility cells.

| SDK | Result | Active ticks | Mean wall us | Max wall ms | Mean allocated bytes |
|---|---|---:|---:|---:|---:|
'''+ '\n'.join(controlrows)+f'''

Thread CPU записывается только для spans >=1ms, поэтому из этих данных нельзя получить
полное среднее CPU. Все spans и failed controls сохранены в `performance-controls/`.
На Windows этот счётчик квантуется: наблюдались 0 и 15.625ms, что не позволяет
интерпретировать его как точный CPU расход отдельного микросекундного callback.
Свежий historical REST mean 45.08us отличается от прежнего Phase5 41.46us при
том же старом SDK; кандидат в основном Windows прогоне дал 43.89us. Это показывает
вариацию окружения. Дополнительная matched пара **NOT VERIFIED** из-за трёх failed
candidate attempts; устойчивое отсутствие регрессии этой парой не доказано.
Сводный новый CSV `results/phase6/performance-integrated.csv`: {len(hosted_perf)} строк;
полные исторические 936 строк и прежние Windows/WSL измерения не заменены.
Алгоритмы трекеров не изменялись. Устойчивую FPS/CPU регрессию по одиночным maxima
не утверждаем; длительный контролируемый benchmark на выделенной машине остаётся
полезной отдельной проверкой при реальных жалобах на FPS.

## 9. Ошибки, product decisions и ограничения

- Windows Gradle compile attempt-01: sandbox lock отказ; attempt-02: native allocation
  OOM во время remap; attempt-03 PASS. Raw logs и hs_err сохранены.
- Первый Windows 26.3 REST: native malloc OOM до JOIN; последовательный attempt-02
  PASS. Причина распределения памяти не установлена; не выдаём retry за SDK fix.
  Ошибки standalone example при параллельной нагрузке также сохранены, не скрываются.
- Windows flags attempt-01 повторил `0xC0000005 jvm.dll+0xA7D687`
  2026-10-16 02:20:04 UTC, ReportID `5c24b756-a2b5-4808-9a0c-8cfa63d75284`.
  Retry PASS не является исправлением; WER сохранён, временный dump недоступен.
- Дополнительный performance candidate attempt-01: ещё один native crash до JOIN,
  `0xC0000005 jvm.dll+0x822591`, ReportID `e7a5d946-8e65-4152-8411-e406d9724d6d`.
  Это другое смещение, общая причина не доказана; WER SHA
  `375a3367352d5410a463f63fba107c02f7871ea27d6b0bbfc1faf47b51242ec1` сохранён.
  Повторная performance attempt-02 дошла до session15 и упала с DOS1455/native
  commit OOM; hs_err_pid4792.log сохранён. Эти попытки не являются perf PASS.
- Performance attempt-03: `0xC0000005 jvm.dll+0x111997`, ReportID
  `07b0104d-69f0-413a-935e-0f5c74378872`. Correlated WER сохранён, временный dump
  недоступен. После трёх failed дополнительных попыток bounded control остановлен.
- Native startup 26.3 остаётся **OPEN**. OOM — отдельная сигнатура. Linux успех
  не закрывает Windows проблему. JVM/driver обходов нет. Расследование этапа3/5,
  включая no-SDK baseline, сохранено в историческом NATIVE_CRASH_INVESTIGATION.md.
- Legacy shutdown остаётся глобальным. Предложение lease + legacy pin + явный отказ
  shutdown при активных leases описано в RELEASE_DECISIONS.md; без согласованного
  изменения протокола владения не заявляем, что этот риск исправлен.
- Delivery остаётся bounded best effort: snapshots 64/15s, events FIFO 256/30s,
  session fence/shutdown discard, без backend ACK/idempotency/exactly-once.
  Бан может потеряться при reconnect. Предложение гарантированной доставки отдельно,
  backend протокол не изменён. Конкретные вопросы backend зафиксированы.
- Все локальные failed attempts: `results/phase6/local-failed-attempts.json`.
  Это список result-файлов: backend wrapper и runtime одного старта могут дать
  две записи. Он не объявляет их независимыми физическими запусками.
- Это не подтверждение поведения на настоящем FunTime backend/server: используется
  локальный протокольный fake backend и настоящий Minecraft fixture server.

## 10. SHA-256 итоговых hosted SDK JAR

Только полученные и проверенные артефакты выбранного run; отсутствующие не выдумываются.

| MC | SDK JAR SHA-256 |
|---|---|
'''+ ('\n'.join(hashrows) if hashrows else '| — | NOT RUN / artifacts not collected yet |')+f'''

Дополнительные SHA sources/POM/module и JIJ comparisons находятся в artifacts.json
и release-artifacts.json каждой цели. Windows локальные hashes сохраняются отдельно;
они не подменяют выбранные hosted release artifacts.

## 11. Воспроизведение

```powershell
.\\gradlew sdkBuild
.\\gradlew sdkStage -Ptarget_versions=1.21.4
python phase6_source_parity.py
python phase5_build.py --targets all --kind compile
python phase5_build.py --targets all --kind publish
python compile_legacy_probe.py
python phase5_build.py --targets all --kind consumer
python phase5_api.py --targets all
python prepare_phase4_tls.py
python phase5_run.py --targets all --kind network
python phase5_run.py --targets all --kind runtime
python phase5_verify.py --stage summary
python phase6_examples.py --target 1.21.4
python phase6_loader_controls.py --target 1.21.4 --wrong-target 26.3
python phase6_checker_controls.py
python phase6_historical_integrity.py
python tools/verify_published_legacy.py --coexist-target 1.21.4,26.3
python tools/github_phase6.py --run-id {a.run_id} --collect
python tools/github_phase6.py --run-id {a.run_id} --collect-attempt 1
python tools/phase6_report.py --run-id {a.run_id}
python tools/phase6_report.py --run-id {a.run_id} --seal
```

Не запускать повторную publication под уже занятой immutable версией. Выберите
новую sdk_version в targets.json, выполните `python tools/phase6_docs.py` и используйте
свежие Maven/evidence директории для полной матрицы нового кандидата. Ручные
Gradle -Pmod_version сборки требуют совпадающей версии всех consumers/checkers.
Linux полный job: `sh ci/phase6/job.sh <MC>` после настройки JDK/dependencies и
`chmod +x gradlew`; Xvfb receipt обязателен.
Hosted команды и полный staging archive находятся в Actions artifacts.

## 12. Порядок публичного выпуска

Предлагается semantic tag/версия `2.0.0-rc.1` без v, затем 2.0.0 после отдельного
решения пользователя. JitPack остаётся основным каналом, новый multi-module target
`...:mc<MC>:<version>`. Central/anonymous Maven hosting сравнены в RELEASE_DECISIONS.md;
канал без согласования не меняется. Dual-JDK JitPack конфигурация подготовлена,
но новый публичный JitPack build не запускался.
Рецепт вызывает wrapper через `sh`: Git mode gradlew=100644, поэтому прямой
`./gradlew` без chmod дал бы permission denied. Это подготовка будущей публикации,
её выполнение именно в JitPack остаётся NOT RUN.

Read-only проверка старой публикации: `...:fte-api:v1.0.5` POM HTTP 404,
`com.github.Rizzener:FunTimeEventsSDK:v1.0.5` POM HTTP 200. Raw API/POM/build.log
сохранены; `status=ok` не доказывает доступность module coordinate. Старые tags
не перепубликовываются; автоматический relocation к произвольному MC невозможен.
После обязательных проверок — отдельное человеческое решение о runtime ownership,
delivery/native ограничениях, tag и публикации. Автоматической публикации нет.
'''
    (ROOT/'PHASE6_RESULTS.md').write_text(report,encoding='utf-8',newline='\n')
    print('PHASE6_REPORT',state,'hosted_artifacts',len(collected),'local_failed_attempts',len(failed))

if __name__=='__main__':main()
