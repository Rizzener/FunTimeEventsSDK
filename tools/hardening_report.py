"""Generate rc.2 reports only from the selected fresh hosted run and artifact bytes."""
import argparse,csv,hashlib,json,shutil,subprocess,zipfile,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'results/release-hardening'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def write(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
def sha(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def integrity():
    rows=[]
    for base,manifest in [(ROOT,ROOT/'results/phase6/evidence-manifest.json'),(ROOT/'poc/multiversion',ROOT/'poc/multiversion/results/phase5/evidence-manifest.json')]:
        files=read(manifest)['files'];bad=[]
        for relative,expected in files.items():
            p=base/relative
            if not p.is_file() or p.stat().st_size!=expected['bytes'] or sha(p)!=expected['sha256']:bad.append(relative)
        rows.append({'manifest':manifest.relative_to(ROOT).as_posix(),'manifest_sha256':sha(manifest),'files':len(files),'mismatches':bad,'status':'PASS' if not bad else 'FAIL'})
    write(BASE/'historical-integrity.json',rows);assert all(r['status']=='PASS' for r in rows),rows
    return rows
def seal():
    out=BASE/'evidence-manifest.json'
    write(out,{'files':{p.relative_to(ROOT).as_posix():{'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(BASE.rglob('*')) if p.is_file() and p!=out}})
    print('SEALED',len(read(out)['files']))
def candidate_controls():
    c=read(ROOT/'targets.json');v=c['sdk_version']
    folder=Path(os.environ['FTE_MAVEN_LOCAL'])/c['maven_group'].replace('.','/')/'mc1.21.4'/v
    before={p.name:sha(p) for p in folder.iterdir() if p.suffix in ['.jar','.pom','.module']};assert len(before)==4
    command=[sys.executable,'tools/hardening_local.py','phase5_build.py','--targets','1.21.4','--kind','publish']
    out=BASE/'local/immutability-control';out.mkdir(parents=True,exist_ok=True)
    assert not (out/'result.json').exists(),'Control is immutable too'
    r=subprocess.run(command,cwd=ROOT,capture_output=True);(out/'raw.txt').write_bytes(r.stdout+r.stderr)
    after={p.name:sha(p) for p in folder.iterdir() if p.suffix in ['.jar','.pom','.module']}
    checks={'duplicate_publish_rejected':r.returncode!=0 and b'Refusing to overwrite published' in r.stdout+r.stderr,'all_four_artifact_hashes_unchanged':before==after}
    write(out/'result.json',{'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'command':command,'exit':r.returncode,'before':before,'after':after})
    print('IMMUTABILITY_CONTROL',checks);assert all(checks.values())
def aggregate_api(run_id):
    hosted=BASE/'hosted'/run_id;run=read(sorted(hosted.glob('*-run.json'))[-1]);attempt=str(run['run_attempt'])
    repository=hosted/('aggregate-'+attempt)/'build/aggregate-maven';assert repository.is_dir()
    out=BASE/'aggregate-api';out.mkdir(exist_ok=True)
    env=dict(os.environ,FTE_PHASE5_EVIDENCE=out.relative_to(ROOT).as_posix(),FTE_MAVEN_LOCAL=str(repository))
    command=[sys.executable,'phase5_api.py','--targets','all']
    with (out/'raw.txt').open('wb') as log:r=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    write(out/'result.json',{'status':'PASS' if r.returncode==0 else 'FAIL','run_id':run_id,'sha':run['head_sha'],'command':command,'repository':str(repository),'exit':r.returncode})
    print('AGGREGATE_API',r.returncode);assert r.returncode==0
def license_control():
    out=BASE/'license-negative-control';out.mkdir(exist_ok=True)
    env=dict(os.environ,FTE_PHASE5_EVIDENCE=out.relative_to(ROOT).as_posix())
    command=[sys.executable,'phase6_artifacts.py','--targets','1.21.4']
    with (out/'raw.txt').open('wb') as log:r=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    artifact=read(out/'1.21.4/release-artifacts.json')
    checks={'missing_source_LICENSE_fails':r.returncode!=0 and artifact['status']=='FAIL' and artifact['checks']['source_license_preserved'] is False,
            'binary_LICENSE_and_custom_metadata_still_correct':all(artifact['checks'][n] for n in ['license_preserved','custom_license_metadata','custom_license_POM'])}
    write(out/'result.json',{'status':'PASS' if all(checks.values()) else 'FAIL','checks':checks,'exit':r.returncode,'command':command})
    print('LICENSE_NEGATIVE_CONTROL',checks);assert all(checks.values())
def source_license_check():
    sys.path.insert(0,str(ROOT))
    from run_phase2 import gradle
    out=BASE/'license-source-check';out.mkdir(exist_ok=True)
    command=gradle(':mc1.21.4:remapSourcesJar',':mc26.3:sourcesJar','-Ptarget_versions=1.21.4,26.3')
    with (out/'raw.txt').open('wb') as log:r=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    checks={};v=read(ROOT/'targets.json')['sdk_version']
    for mc in ['1.21.4','26.3']:
        jar=ROOT/'targets'/('mc'+mc)/'build/libs'/('mc'+mc+'-'+v+'-sources.jar')
        with zipfile.ZipFile(jar) as z:checks[mc]='LICENSE' in z.namelist() and z.read('LICENSE')==(ROOT/'LICENSE').read_bytes()
        shutil.copy2(jar,out/jar.name)
    write(out/'result.json',{'status':'PASS' if r.returncode==0 and all(checks.values()) else 'FAIL','checks':checks,'command':command,'exit':r.returncode})
    print('SOURCE_LICENSE_CHECK',checks);assert r.returncode==0 and all(checks.values())
def progress_log(run_id,mc):
    import urllib.error,time
    sys.path.insert(0,str(ROOT))
    from tools.github_phase6 import credential,get,download
    token=credential();jobs=get('actions/runs/'+run_id+'/jobs?per_page=100',token)['jobs']
    job=next(j for j in jobs if j['name']=='target ('+mc+')')
    out=BASE/'hosted'/run_id/'progress';out.mkdir(exist_ok=True)
    path=out/(str(job['id'])+'-'+time.strftime('%Y%m%dT%H%M%S')+'.txt')
    try:
        download('actions/jobs/'+str(job['id'])+'/logs',token,path)
        print('\n'.join(path.read_text(encoding='utf-8',errors='replace').splitlines()[-12:]))
    except urllib.error.HTTPError as e:
        write(path.with_suffix('.json'),{'status':'NOT AVAILABLE','http_status':e.code,'job_id':job['id'],'job_status':job['status']});print('LIVE_LOG_NOT_AVAILABLE',e.code,job['status'])
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--run-id',required=True);ap.add_argument('--seal',action='store_true');ap.add_argument('--candidate-controls',action='store_true');ap.add_argument('--aggregate-api',action='store_true');ap.add_argument('--license-control',action='store_true');ap.add_argument('--source-license-check',action='store_true');ap.add_argument('--progress-target');a=ap.parse_args()
    if a.progress_target:progress_log(a.run_id,a.progress_target);return
    if a.source_license_check:source_license_check();return
    if a.license_control:license_control();return
    if a.aggregate_api:aggregate_api(a.run_id);return
    if a.candidate_controls:candidate_controls();return
    if a.seal:seal();return
    c=read(ROOT/'targets.json');v=c['sdk_version'];assert v=='2.0.0-rc.2'
    hosted=BASE/'hosted'/a.run_id
    run=read(sorted(hosted.glob('*-run.json'))[-1]);jobs=read(sorted(hosted.glob('*-jobs.json'))[-1])['jobs'];attempt=str(run['run_attempt'])
    assert run['id']==int(a.run_id) and run['head_branch']=='integration/multiversion-rc'
    assert run['status']=='completed' and run['conclusion']=='success',(run['status'],run['conclusion'])
    assert len(jobs)==18 and all(j['conclusion']=='success' for j in jobs),[(j['name'],j['conclusion']) for j in jobs]
    summary=hosted/('phase6-summary-'+attempt)
    assert read(summary/'aggregate-comparison.json')['status']=='PASS'
    assert read(summary/'summary.json')['status']=='FUNCTIONAL_PASS_NATIVE_OPEN'
    protected=['core','platform','targets','gradle','targets.json','build.gradle.kts','settings.gradle.kts','gradle.properties']
    assert not subprocess.check_output(['git','diff',run['head_sha'],'--',*protected],cwd=ROOT),'Current producer differs from tested source'
    metadata=read(sorted(hosted.glob('*-artifacts.json'))[-1])['artifacts'];digests={}
    for item in metadata:
        if item['name'].endswith('-'+attempt):
            archive=hosted/(item['name']+'.zip');digests[item['name']]=('sha256:'+sha(archive)==item['digest'])
    assert len(digests)==17 and all(digests.values()),digests
    write(BASE/'hosted-archive-digests.json',{'status':'PASS','run_id':a.run_id,'attempt':attempt,'checks':digests})
    root=hosted/('aggregate-'+attempt)/'build/aggregate-maven'
    candidate=BASE/'candidate'/v;files={};matrix=[];hashrows=[];perf=[];raw=[];sources={};binary_equal={}
    for spec in c['targets']:
        mc=spec['minecraft'];folder=hosted/('target-'+mc+'-'+attempt)/'results/release-hardening/github'/a.run_id/attempt/mc
        prov=read(folder/'provenance.json');assert prov['identity']=={'sha':run['head_sha'],'run_id':a.run_id,'run_attempt':attempt,'version':v}
        status={k:read(folder/(k+'.json'))['status'] for k in ['api','artifacts','network','runtime','release-artifacts']}
        assert all(x=='PASS' for x in status.values()),(mc,status)
        assert read(folder/'lease-api/result.json')['status']=='PASS'
        assert read(folder/'doc-examples/result.json')['status']=='PASS'
        assert read(folder/'../source-parity.json')['status']=='PASS'
        for kind in ['compile','publish','consumer']:
            assert any(read(p)['status']=='PASS' for p in folder.glob(kind+'/attempt-*/result.json')),(mc,kind)
        matrix.append('| '+mc+' | '+str(spec['java'])+' | PASS | PASS | PASS | PASS | PASS | PASS | PASS |')
        package=read(folder/'release-artifacts.json')
        relative=Path(c['maven_group'].replace('.','/'))/('mc'+mc)/v
        for name,digest in package['sha256'].items():
            p=root/relative/name;assert sha(p)==digest
            dest=candidate/relative/name;dest.parent.mkdir(parents=True,exist_ok=True)
            if dest.exists():assert sha(dest)==digest,'Never overwrite candidate'
            else:shutil.copy2(p,dest)
            files[dest.relative_to(candidate).as_posix()]={'sha256':digest,'bytes':dest.stat().st_size}
            if name.endswith('.jar') and not name.endswith('-sources.jar'):
                hashrows.append('| '+mc+' | `'+digest+'` |')
                previous=BASE/'hosted/38874304432/aggregate-1/build/aggregate-maven'/relative/name
                binary_equal[mc]=previous.is_file() and sha(previous)==digest
                with zipfile.ZipFile(p) as z:assert 'net/funtimeevents/api/LeaseGenerationFixture.class' not in z.namelist()
            if name.endswith('-sources.jar'):
                with zipfile.ZipFile(p) as z:
                    names=z.namelist();code=z.read('net/funtimeevents/internal/platform/FabricPlatform.java').decode()
                    checks={'unique_entries':len(names)==len(set(names)),'RuntimeLease_source':'net/funtimeevents/api/RuntimeLease.java' in names,
                            'full_custom_LICENSE':'LICENSE' in names and z.read('LICENSE')==(ROOT/'LICENSE').read_bytes(),
                            'no_lease_fixture':'net/funtimeevents/api/LeaseGenerationFixture.java' not in names,
                            'namespace':'net.minecraft.class_310' in code if spec['java']==21 else 'net.minecraft.client.Minecraft' in code and 'net.minecraft.class_310' not in code,
                            'no_experimental_sources':not any('/poc/' in n or n.endswith(('TrackerSliceAPI.java','BanSliceAPI.java','PocEventsAPI.java','PocCore.java')) for n in names)}
                    assert all(checks.values()),(mc,checks);sources[mc]=checks
        rt=read(folder/'runtime.json')
        for mode,detail in rt['modes'].items():
            assert detail['status']=='PASS'
            for r in detail['runs']:
                assert r['status']=='PASS' and all(r['checks'].values()),(mc,mode,r)
                p=r['tick_and_callback_performance']['sdk_tick_active']
                perf.append({'minecraft':mc,'mode':mode,**p})
        raw.extend(read(p) for p in folder.glob('runtime-*/attempt-*/result.json'))
    assert len(files)==52
    assert len(binary_equal)==13 and all(binary_equal.values()),binary_equal
    write(BASE/'packaging-binary-immutability.json',{'status':'PASS','baseline_run':38874304432,'final_run':a.run_id,'all_13_production_JAR_bytes_unchanged':binary_equal,'changed_outputs':'Sources JAR and associated module metadata; unpublished draft outputs retained in separate run repositories.'})
    manifest={'status':'PASS','version':v,'run_id':a.run_id,'attempt':attempt,'sha':run['head_sha'],'files':files};write(candidate/'manifest.json',manifest)
    write(BASE/'sources-verification.json',{'status':'PASS','targets':sources})
    write(BASE/'performance.json',perf)
    with (BASE/'performance.csv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(perf[0]));writer.writeheader();writer.writerows(perf)
    stats={'starts':len(raw),'PASS':sum(r['status']=='PASS' for r in raw),'JOIN':sum(r.get('runtime',{}).get('joins',0) for r in raw),'DISCONNECT':sum(r.get('runtime',{}).get('disconnects',0) for r in raw),
           'callbacks':sum(len(r.get('runtime',{}).get('callbacks',[])) for r in raw),'payloads':sum(len(r.get('runtime',{}).get('payloads',[])) for r in raw)}
    assert stats['starts']==54 and stats['PASS']==54,stats
    write(BASE/'production-counts.json',stats)
    history=integrity()
    freeze={p.relative_to(ROOT).as_posix():sha(p) for b in ['core/src/main','platform','targets'] for p in (ROOT/b).rglob('*') if p.is_file() and 'build' not in p.relative_to(ROOT).parts}
    write(BASE/'source-freeze.json',freeze)
    sys.path.insert(0,str(ROOT))
    from phase6_source_parity import normalize
    old_facade=next((ROOT/'poc/multiversion/platform/common').rglob('VersionAccess.java'))
    new_facade=next((ROOT/'platform/common').rglob('VersionAccess.java'))
    facade_equal=normalize(old_facade.read_text(encoding='utf-8'))==new_facade.read_text(encoding='utf-8')
    java_count=sum(1 for name in freeze if name.endswith('.java'))
    assert facade_equal and java_count==73,(facade_equal,java_count)
    write(BASE/'source-parity-supplement.json',{'status':'PASS','common_VersionAccess_matches_accepted_Phase5':facade_equal,'production_Java_sources':java_count,
          'unchanged_accepted_sources':69,'reviewed_ownership_changes':3,'additive_RuntimeLease':1})
    verdict={'status':'PASS','version':v,'run_id':int(a.run_id),'attempt':int(attempt),'sha':run['head_sha'],'jobs':len(jobs),'targets':13,'artifacts':52,'stats':stats,'native':'OPEN','matched_performance':'NOT VERIFIED'};write(BASE/'verdict.json',verdict)
    archive=read(BASE/'archive-receipt.json') if (BASE/'archive-receipt.json').exists() else {'status':'NOT RUN'}
    if archive['status']=='PASS':assert sha(ROOT/archive['archive'])==archive['sha256']
    perfrows=[]
    for spec in c['targets']:
        mc=spec['minecraft'];rows=[x for x in perf if x['minecraft']==mc]
        perfrows.append('| '+mc+' | '+f"{min(r['mean_ns'] for r in rows)/1000:.2f}–{max(r['mean_ns'] for r in rows)/1000:.2f}"+' | '+f"{max(r['max_ns'] for r in rows)/1e6:.2f}"+' | '+f"{min(r['mean_bytes'] for r in rows):.0f}–{max(r['mean_bytes'] for r in rows):.0f}"+' |')
    earlier=[]
    for folder in sorted((BASE/'hosted').iterdir()):
        snapshots=sorted(folder.glob('*-run.json'))
        if snapshots:
            item=read(snapshots[-1]);earlier.append(f"- [{item['id']}]({item['html_url']}), attempt {item['run_attempt']}, `{item['head_sha']}`: **{item['conclusion'] or item['status']}**.")
    text=f'''# Release hardening — {v}

## Итог

Функциональные обязательные проверки: **PASS**. Публичная публикация, tag, GitHub
Release и merge не выполнены. Решение о выпуске остаётся за владельцем проекта.
Windows native crash 26.3 — **OPEN**. Дополнительный performance matched pair —
**NOT VERIFIED**; он не включён в PASS матрицы.

Проверенный commit: `{run['head_sha']}`. SDK ownership-код впервые зафиксирован в
`4a17019`; последующие изменения candidate fixtures не меняют его бинарные входы.
Ветка `integration/multiversion-rc`, main не изменена. rc.1 не перезаписывался.
Повторная проверка прежних manifests: {history[0]['files']} файлов этапа 6 и
{history[1]['files']} файлов этапа 5, **0 несовпадений**.

## Изменения SDK

- Additive `FteConfig.Builder.buildLease(): RuntimeLease`; `RuntimeLease implements AutoCloseable`.
- Объект runtime служит generation token. Счётчик leases и legacy pin принадлежат
  клиентскому потоку. Закрытие handle защищено AtomicBoolean и идемпотентно.
- Последний lease останавливает неприкреплённый legacy pin runtime. Legacy build()
  возвращает прежний тип и удерживает pin. shutdown() явно отказывает при leases.
- Фоновые ownership-вызовы синхронно передаются клиентскому executor, без lock во
  время ожидания. CLIENT_STOPPING запрещает acquire, закрывает runtime и завершает
  ожидающие операции ошибкой; старые handles не затрагивают новое поколение.
- Ошибка инициализации сохраняет исходное исключение; cleanup пытается закрыть
  каждый ресурс и добавляет ошибки закрытия как suppressed.
- Fabric bridge регистрируется один раз. Игровой lifecycle и интервалы не изменены.

Полный контракт и ограничения: [RUNTIME_OWNERSHIP.md](docs/RUNTIME_OWNERSHIP.md).
Два независимых legacy build() невозможно учитывать как отдельных владельцев без
изменения старого API. Их глобальный shutdown требует координации host-модом.

## Неизменённые компоненты

Core, игровые алгоритмы, regex/zones, расписание, REST/WSS/SSE/Captcha транспорты,
JSON/gzip и backend protocol сохранены. CI source parity проверяет 71 исходник;
common VersionAccess дополнительно сравнен с принятым Phase 5. Итого из 72 прежних
исходников 69 сохранены, три ownership-файла имеют reviewed exceptions с hash
baseline/candidate. Новый RuntimeLease — additive класс: всего 73 Java источника.
Eight leaves/thirteen targets и
прежние mappings/namespace остаются прежними. Нет DI, EventBus или reflection.

## Свежий hosted CI

Run [{a.run_id}]({run['html_url']}), attempt **{attempt}**, **18/18 jobs success**.
Каждая цель — свежий checkout, staging и Maven Local, без старых SDK artifacts.
Provenance сверяет commit/run/attempt/version и действительный pinned JDK runtime.
Aggregate sdkBuild/sdkStage без фильтра совпал по SHA-256 с независимой матрицей:
**52/52 файлов**. Два production Loader controls и required matrix — PASS.

История прогонов, включая отменённые:

{chr(10).join(earlier)}

Первый прогон остановлен для добавления обязательной конкурентной первой
инициализации и изоляции cleanup receipts. Второй — после обнаружения отсутствующего
LICENSE в sources JAR. Их результаты не заполняют новую матрицу.
Required matrix отменённых прогонов завершился FAIL из-за незавершённых целей;
отмена не превращена в успешную проверку.
Полные ZIP/logs/metadata сохранены. Отсутствующий canceled artifact blob отмечается
отдельным NOT COLLECTED receipt и не считается проверкой.
При collection были DNS/read timeout и усечённый summary ZIP предварительного
run 38874304432 (146393 bytes; SHA 77e1c98b… не совпал с server digest).
Исходный ZIP и partial files сохранены, применён системный HTTPS curl с TLS verification.
Секретные signed URLs не записываются. Финальные 17 архивов принимаются только после
совпадения полного SHA-256 с GitHub metadata; ошибки collection не считаются ошибками
SDK runtime и не превращаются в PASS.

## Матрица новых артефактов

В каждой ячейке runtime проверяются offline/REST/WSS/individual flags, golden
payload, hover и реальные callbacks, JOIN/DISCONNECT/reconnect, два consumer-мода,
worker acquire/conflicts/shutdown/close, первая инициализация и CLIENT_STOPPING.

| Minecraft | Java | Compile/unit/parity | Maven/JIJ/metadata | Legacy API | REST/WSS/SSE/Captcha | Trackers/lifecycle | RuntimeLease | Docs consumer |
|---|---:|---|---|---|---|---|---|---|
{chr(10).join(matrix)}

Всего **{stats['starts']} production starts/{stats['PASS']} PASS**, {stats['JOIN']} JOIN,
{stats['DISCONNECT']} DISCONNECT, {stats['callbacks']} callbacks, {stats['payloads']} payload.
26.3 дополнительно имеет два REST запуска. Все семь трекеров и ServerContext
проверяются прежними golden fixtures, не прямым вызовом одного парсера.

Локально Windows 1.21.4: compile, publication, consumers, API, network и два offline
production starts прошли. Первая попытка имела предварительный scope fixtures;
вторая содержит окончательный concurrent-init тест. Локальная сводка runtime
NOT VERIFIED из-за незапущенных локально rest/ws/flags; она не заменяет hosted matrix.
Негативный duplicate-publication control отклоняет повтор rc.2 и сохраняет hashes
всех четырёх Maven файлов. Все raw logs и результаты отрицательной проверки сохранены.

## Ownership и API compatibility

Все 36 заявленных legacy public classes: binary descriptors сохранены; точный
старый Java source probe компилируется против каждой цели; precompiled probe
запущен в production с новым SDK. Новый AutoCloseable/close/buildLease проверен javap.
Внутренние tracker/bootstrap/util вызовы остаются явно мигрируемыми, как согласовано.

Два реальных Fabric consumer-мода содержат одну и ту же nested SDK dependency.
Loader выбирает один fte-api. Проверены равные/конфликтующие конфигурации, legacy
shutdown rejection, 24 worker leases и конкурентное закрытие общего handle,
legacy pin, идемпотентный close, последний peer lease, устаревшее поколение,
rollback build/buildLease и повторная инициализация.
Четыре worker одновременно впервые создают runtime без legacy pin и освобождают
его последним close. CLIENT_STOPPING с двумя открытыми leases и четырьмя worker
вызовами, конкурирующими с остановкой, освобождает runtime/сеть, запрещает acquire
и не оставляет FTE threads. Тест не фиксирует точное число задач в очереди на момент stop.
Wrong Minecraft target и несовместимые exact SDK requirements отвергаются Loader.
Не обещается ABI произвольных неизвестных модов между разными Minecraft targets.

## Release hygiene и доставка

SDK metadata: `LicenseRef-FunTimeEvents-Backend-Restriction`; POM и документация:
**MIT License with Backend Restriction**, точный LICENSE внутри binary и sources JAR.
Это не стандартная MIT. При аудите обнаружено отсутствие LICENSE в предварительных
sources JAR; исправлена общая Gradle packaging convention и добавлена обязательная
проверка всех 13 targets. Старые outputs сохранены; финальное staging создано на
чистых runners, предыдущие Maven-файлы не перезаписывались.
Все 13 binary SDK JAR побайтно совпадают с предварительным run 38874304432:
исправление затронуло sources JAR и связанную Gradle Module Metadata, не бинарный SDK.
Негативный контроль действительно получает FAIL на прежнем sources JAR без LICENSE;
этот ожидаемый отказ сохранён отдельно, внешний статус negative control — PASS.
JAR/sources/POM/module, core exactly once, Java 21/25 и namespace проверены для всех целей;
experimental/test classes, keys/certificates/fixture dependencies отсутствуют.

Будущие координаты: `{c['maven_group']}:mc<точная-версия>:{v}`.
Примеры с modImplementation (legacy), implementation (modern) и include компилируются
отдельными Maven builds без скрытого Maven Local. JitPack остаётся каналом по плану;
новый RC в JitPack не запрашивался и публично не публиковался.
Старый документированный `com.github.Rizzener.FunTimeEventsSDK:fte-api:v1.0.5`
ранее дал 404; опубликованный старый v1.0.5 получался как
`com.github.Rizzener:FunTimeEventsSDK:v1.0.5`. Это факт конкретной версии, не всех тегов.
Автоматической relocation на произвольный MC target нет. См. DOC.md/RELEASE_DECISIONS.md.

Wire protocol и bounded best effort не менялись. BanPayload — историческое событие;
coordinates — ограниченное по актуальности наблюдение; snapshots — заменяемое
состояние. Потеря ban при reconnect/TTL/overflow/shutdown остаётся возможной.
[Предложение backend](docs/BAN_DELIVERY_PROPOSAL.md) описывает event ID, commit ACK,
idempotency, retention/backoff/reconnect, outbox и необходимые одновременные изменения.
ACK/retries не реализованы; at-least-once/exactly-once не обещаются.

## Производительность и native

Новые production measurements: диапазон средних callback costs по четырём разным
режимам каждого target. Это listener span, не whole-frame FPS benchmark. Максимумы
wall-clock — отдельные наблюдения, не постоянные затраты. Полные числа — performance.csv.

| Minecraft | Среднее active callback, μs (min–max режимов) | Max wall-clock, ms | Средняя allocation, bytes (min–max) |
|---|---:|---:|---:|
{chr(10).join(perfrows)}

Предыдущие измерения этапов 5/6 сохранены неизменными. Matched pair из этапа 6
остаётся NOT VERIFIED: candidate attempts завершались native faults/OOM. Новая
строгая matched pair здесь NOT RUN: локальный C: имел около 3 GiB свободного места,
предыдущие Windows прогоны уже показали resource/native failures; Linux CI fixtures
дают измерения нового RC, но не являются matched pair с прежним RC. Стабильная
регрессия не доказана; строгая эквивалентность performance также не заявляется.

Windows 26.3 native startup — OPEN; ранее crash воспроизводился и без SDK.
Новых Windows 26.3 данных этот этап не добавляет. Все новые hosted Linux 26.3
production starts проходят с обычными Xvfb/Mesa/Vulkan dependencies; это не закрывает
Windows issue. Новые непроверенные JVM/driver flags не добавлены.

## Evidence и воспроизведение

Fresh evidence: `results/release-hardening/hosted/{a.run_id}/`, локальные попытки —
`results/release-hardening/local/`. Candidate Maven: `results/release-hardening/candidate/{v}/`.
Все {len(digests)} selected raw Actions ZIP сверены с GitHub artifact digest.
Durable local archive: **{archive['status']}**, receipt —
`results/release-hardening/archive-receipt.json`, каталог `release-evidence/{v}/`.
Он отделён от временного Actions storage. Off-device backup **NOT RUN**: внешнее
место хранения не согласовано. Локальный архив не защищает от потери этого диска.
Исторические измерения и неудачные matched-pair attempts дополнительно сохранены
с SHA manifest в `release-evidence/2.0.0-rc.2/historical-performance/`.

```powershell
python tools/hardening_local.py phase5_build.py --targets 1.21.4 --kind compile
python tools/hardening_local.py phase5_build.py --targets 1.21.4 --kind publish
python tools/hardening_local.py phase5_build.py --targets 1.21.4 --kind consumer
python tools/hardening_local.py phase5_api.py --targets 1.21.4
python tools/hardening_local.py phase6_lease_api.py --target 1.21.4
python prepare_phase4_tls.py
python tools/hardening_local.py phase5_run.py --targets 1.21.4 --kind network
python tools/hardening_local.py phase5_run.py --targets 1.21.4 --kind runtime
python tools/hardening_local.py phase5_verify.py --targets 1.21.4 --stage runtime
```

Для повторной публикации в тестах используйте новый пустой локальный Maven root:
immutable rc.2 из уже проверенного каталога не перезаписывается. Полная Linux
матрица выполняется `.github/workflows/release-candidate.yml` / `ci/phase6/job.sh`;
Xvfb wrapper — `ci/phase5/x11-runtime.sh`. Сборка на Gradle {c['gradle']}, Loom
{c['loom']}, Loader {c['loader']}, pinned Temurin 21.0.9+10/25.0.4+7, FAPI из targets.json.

Сбор evidence: `python tools/hardening_local.py tools/github_phase6.py --run-id {a.run_id} --collect`.
Отчёт: `python tools/hardening_report.py --run-id {a.run_id}`. Архив:
`python tools/archive_release_evidence.py --run-id {a.run_id}` (однократно).
Seal: `python tools/hardening_report.py --run-id {a.run_id} --seal`.

## SHA-256 production SDK JAR

| Target | SHA-256 |
|---|---|
{chr(10).join(hashrows)}

## Оставшиеся ограничения и решение о выпуске

Native Windows issue OPEN; strict matched performance NOT VERIFIED; backend delivery
upgrade OPEN; только локальный durable archive, без внешней копии. Singleton принимает
только одинаковые конфигурации; legacy build callers требуют координации shutdown.
Фоновая ownership-операция ждёт client executor: нельзя блокировать его join такого
worker. JitPack actual publication/резолюция нового RC — NOT RUN по запрету публикации.

Рекомендуемый кандидат — {v}; rc.1 остаётся неизменным. Следующий шаг — отдельное
решение владельца о публичной публикации и допустимости перечисленных ограничений.
Смена Maven-канала, tag/Release и merge требуют отдельного разрешения.
'''
    (ROOT/'RELEASE_HARDENING_RESULTS.md').write_text(text,encoding='utf-8',newline='\n')
    criteria=[('RuntimeLease API / поколения / idempotent close','PASS'),('Legacy public signatures + source + old binary probe','PASS'),('Legacy pin и явный shutdown rejection','PASS'),('Два реальных consumer-мода и concurrent first initialization','PASS'),('CLIENT_STOPPING, pending workers и очистка ресурсов','PASS'),('13 свежих hosted production targets и required matrix','PASS'),('Maven staging, 52 files, JIJ, namespace, Java constraints','PASS'),('REST/WSS/SSE/Captcha + все игровые golden fixtures','PASS'),('License with Backend Restriction / release hygiene','PASS'),('Сохранность rc.1 и historical manifests','PASS'),('Durable local evidence archive',archive['status']),('Off-device backup','NOT RUN'),('Performance нового кандидата: измерения','PASS'),('Строгая matched pair / устранение неопределённости regression','OPEN'),('Windows native startup 26.3','OPEN'),('Backend ACK/idempotency/retention agreement','OPEN'),('Публичная публикация JitPack нового RC','NOT RUN'),('Tag / GitHub Release / merge','NOT RUN'),('Решение владельца о выпуске','OPEN')]
    checklist='# Release checklist — '+v+'\n\nСтатусы: PASS / FAIL / OPEN / NOT RUN. OPEN и NOT RUN не являются PASS.\n\n| Критерий | Статус |\n|---|---|\n'+'\n'.join('| '+n+' | **'+s+'** |' for n,s in criteria)+'\n\nПроверенный SHA: `'+run['head_sha']+'`. [Hosted run]('+run['html_url']+').\n\nПодробности, матрица, команды, ограничения и hashes — [RELEASE_HARDENING_RESULTS.md](RELEASE_HARDENING_RESULTS.md).\nПубличных действий не выполнено. Для выпуска нужно отдельное решение владельца.\n'
    (ROOT/'RELEASE_CHECKLIST.md').write_text(checklist,encoding='utf-8',newline='\n')
    print('HARDENING_REPORT_PASS',verdict)
if __name__=='__main__':main()
