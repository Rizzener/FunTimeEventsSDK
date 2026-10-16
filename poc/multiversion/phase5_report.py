"""Render factual progress/results from fail-closed JSON evidence."""
import datetime,json
from pathlib import Path
from phase5_verify import ROOT,CONFIG,summary,load
from phase5_linux_results import main as linux_summary,PREFIXES

r=summary()
linux=linux_summary()['targets']
integrity_path=ROOT/'results/phase5/integrity.json';cross_path=ROOT/'results/phase5/cross-os.json'
integrity=load(integrity_path).get('status') if integrity_path.exists() else 'NOT VERIFIED'
cross=load(cross_path).get('status') if cross_path.exists() else 'NOT VERIFIED'
overall='FUNCTIONAL_PASS_NATIVE_OPEN' if r['status']=='FUNCTIONAL_PASS_NATIVE_OPEN' and all(v['status']=='PASS' for v in linux.values()) and integrity=='PASS' and cross=='PASS' else 'NOT VERIFIED'
lines=['# Phase 5 — результаты','',
 'Обновлено: '+datetime.datetime.now(datetime.timezone.utc).isoformat(),'',
 '**Статус Windows + локальный Linux: '+overall+'**. Native startup 26.3: **OPEN**. Production release не публиковался.','',
 'Только изолированный `poc/multiversion`; экспериментальная версия `'+CONFIG['sdk_version']+'`, group `net.funtimeevents.poc`.','',
 '## Матрица Windows','',
 '| Minecraft | Java | Fabric API | Compile | Maven | Consumer | Packaging | API | Transport | Runtime |',
 '|---|---|---|---|---|---|---|---|---|---|']
for t in CONFIG['targets']:
    row=r['targets'][t['minecraft']]
    lines.append('| '+' | '.join([t['minecraft'],str(t['java']),t['fabric_api']]+[row[k] for k in ['compile','publish','consumer','artifacts','api','network','runtime']])+' |')
lines += ['', '## Матрица native WSL / Linux','',
 '| Minecraft | Worker | Compile | Maven | Consumer | Packaging | API | Transport | Runtime |',
 '|---|---|---|---|---|---|---|---|---|']
for t in CONFIG['targets']:
    row=linux[t['minecraft']]
    lines.append('| '+' | '.join([t['minecraft'],row['prefix'].split('/')[-1]]+[row[k] for k in ['compile','publish','consumer','artifacts','api','network','runtime']])+' |')
lines += ['', 'Каждый Runtime PASS требует отдельно offline, REST, WSS и disable flags. Отсутствующая проверка получает NOT VERIFIED. Компиляция сама по себе поддержку не подтверждает. Подробные assertions, session payload и нормальные exit codes: `results/phase5/<MC>/runtime.json`.','',
 '## Производительность','',
 'Числа ниже — измеренные SDK/Fabric callback spans в тестовом клиентском потоке; соседние локальные проверки создают смешанную нагрузку. Это не FPS benchmark. Raw counters сохраняют count/sum/max nanos и allocated bytes; JSON runtime.json содержит нормализованные mean/max и затраты каждого трекера.','',
 '| Среда / Minecraft / mode | Active mean/max µs | Idle mean µs | Active mean bytes | FTE threads после shutdown |',
 '|---|---|---|---|---|']
for environment,prefix in [('Windows','results/phase5')]+[(p.split('/')[-1],p) for p in PREFIXES]:
    for t in CONFIG['targets']:
        p=ROOT/prefix/t['minecraft']/'runtime.json'
        if not p.exists():continue
        data=load(p)
        for mode in ['rest','ws']:
            good=[x for x in data.get('modes',{}).get(mode,{}).get('runs',[]) if x['status']=='PASS']
            if not good:continue
            run=good[-1];perf=run['tick_and_callback_performance'];a=perf['sdk_tick_active'];idle=perf['sdk_tick_idle']
            raw=load(ROOT/run['path'].replace('\\','/'));control=raw.get('performance_control',{})
            label=environment+(' CPU'+str(control['cpu_limit'])+' diagnostic' if control else '')
            lines.append('| %s / %s / %s | %.3f / %.3f | %.3f | %.1f | 0 |'%(label,t['minecraft'],mode,a['mean_ns']/1000,a['max_ns']/1000,idle['mean_ns']/1000,a['mean_bytes']))
lines += ['', 'Все измеренные modes/categories, включая idle, сообщения, Dungeon/TAB/Mine/Hell и allocations: [performance.csv](results/phase5/performance.csv), [performance.json](results/phase5/performance.json). Отсутствующие измерения явно отмечены NOT MEASURED / NOT VERIFIED. Tracker spans измеряют время; allocations доступны для общего клиентского callback span, а не отдельно для каждого трекера.']
lines += ['', 'Исследование выбросов: Windows WSS 1.21.5 active mean 59.464 µs при 2176 измерениях и max 37164.5 µs; среднее остальных 2175 равно 42.405 µs. Windows REST TAB 26.1.1/26.1.2/26.3 имеет только по 9 измерений, один max составляет более половины их суммы; средние остальных восьми 127.763/87.488/130.712 µs. Это арифметическое описание агрегатов, а не удаление наблюдений. Исходные mean/max сохранены. Причину отдельных пауз (планировщик/JIT/GC) эти счётчики не устанавливают; устойчивую регрессию конкретной версии по ним утверждать нельзя. Все случаи такого доминирования max: [performance-outliers.json](results/phase5/performance-outliers.json). Алгоритмы ради результатов измерений не изменялись.']
control_path=ROOT/'results/phase5/local-environment/performance-cpu-control/analysis.json'
if control_path.exists():
    control=load(control_path)
    lines += ['', 'Дополнительное исследование WSL 26.2 REST: исходный max 266.642 ms сохранён. Повторы с тем же опубликованным SDK при 2/4 CPU и Thread CPU instrumentation: [analysis.json](results/phase5/local-environment/performance-cpu-control/analysis.json).','',
        '| Контроль | Active mean µs | Active max ms | Thread CPU у самого длинного span, ms |','|---|---|---|---|']
    for row in control['rows']:
        cpu=row['long_active_spans'][0]['thread_cpu_ns']/1e6 if row['long_active_spans'] else None
        lines.append('| %s | %.3f | %.3f | %s |'%(row['case'],row['mean_us'],row['max_us']/1000,'NOT MEASURED' if cpu is None else '%.3f'%cpu))
    lines += ['', 'Одинаковый SDK SHA и прежние функциональные assertions проверены отдельно. Дополнительная instrumentation находится только в consumer; основная матрица и исходные source ZIP сохранены. Повторы не устанавливают первопричину старой паузы и не являются обещанием FPS. Cgroup counters и команды сохранены рядом. Семантика измерения и ограничение точности: [JDK ThreadMXBean](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ThreadMXBean.html).']
    lines += ['', 'Исходные 266.642 ms не воспроизвелись даже при повторном лимите 2 CPU: max 6.898 ms (Thread CPU 6.845 ms); при 4 CPU max 5.025 ms (Thread CPU 4.933 ms). SDK, consumer, peer и server JAR между двумя контролями побайтно одинаковы. Throttling cgroup присутствует и учитывается накопительными counters с отдельной delta второго контроля; он сам по себе не устанавливает причину старого span. Устойчивую регрессию 26.2 эти данные не подтверждают. Точная причина первоначальной паузы остаётся неустановленной; гарантии worst-case latency и production FPS из fixtures не следуют. Алгоритмы SDK ради измерений не менялись.']
lines += ['', 'Dungeon/TAB/Mine/Hell: `tracker_performance`; entity visits, TextDisplay reads и block reads: `reads` для каждой из 21 сессий. Нулевое число FTE threads — реальная проверка живых потоков после shutdown, а не число полей executor.','',
 '## Архитектура и различия API','',
 'Один multi-project, общий Java 21 core, общие трекеры/transport. Legacy использует Mojang mappings + `net.fabricmc.fabric-loom-remap`/remapJar, modern — `net.fabricmc.fabric-loom`/обычный unobfuscated JAR. Gradle 9.6.1, Loom 1.17.21, Loader 0.19.5, installer 1.1.2. Полная конфигурация каждого target: `targets.json`.','',
 'Реестр: [PHASE5_API_DIFFERENCES.md](PHASE5_API_DIFFERENCES.md). Четыре места несовместимости, 8 небольших leaf-файлов; общие трекеры не копировались. Фактические javap и authlib versions: `results/phase5/signatures`. Исправления только проверочных сценариев: [PHASE5_TEST_FIXES.md](PHASE5_TEST_FIXES.md).','',
 'Сравнение с замороженными исходниками 4C: `results/phase5/frozen-sources.json`. API: 36 прежних публичных классов, JVM descriptors и компиляция старого исходника; отдельно production consumer выполняет заранее собранный против старого SDK LegacyApiProbe с новым SDK.','',
 '## Упаковка и SHA-256','',
 'JAR, sources, POM, Module Metadata, единственная копия core, bytecode, namespace, fabric.mod.json, два accessor mixin, отсутствие внешних библиотек внутри SDK и JIJ consumers проверяются исполняемым `phase5_verify.py --stage artifacts`. Consumer использует только тестовый Maven repository при явном `phase5_repository`, без fallback на Maven Local. Нативная загрузка NESTED/development=false подтверждается отдельно runtime.','',
 '| Minecraft | Проверенный SDK JAR SHA-256 |','|---|---|']
for t in CONFIG['targets']:
    p=ROOT/'results/phase5'/t['minecraft']/'artifacts.json'
    if p.exists():
        a=load(p);hashes=a.get('sha256',{});name='mc'+t['minecraft']+'-'+CONFIG['sdk_version']+'.jar'
        if name in hashes:lines.append('| '+t['minecraft']+' | `'+hashes[name]+'` |')
lines += ['', 'Все четыре опубликованных файла каждого target сохранены в `results/phase5/artifacts/`; соответствующие publish/result.json содержат их SHA. Прежние экспериментальные версии не перезаписывались. [Integrity](results/phase5/integrity.json) проверяет 76 замороженных SDK исходников, принятые Phase 4 артефакты, прежние Maven Local версии и неизменность tracked release SDK.','',
 'Полный SHA-256 список сохранённых evidence и исполняемых исходников: [evidence-manifest.json](results/phase5/evidence-manifest.json). Результат его отдельной проверки: [manifest-verification.json](results/phase5/manifest-verification.json). Manifest создаётся после остановки workers; исключает себя и собственный verification receipt.','',
 'Межсистемные SHA: [cross-os.json](results/phase5/cross-os.json). JAR/sources/module сравниваются побайтно. POM сохраняет разные исходные SHA и byte_equal=false при различии только CRLF/LF; допускается исключительно замена CRLF на LF, XML content должен совпадать.','',
]
native_path=ROOT/'results/phase5/native-startup.json'
if native_path.exists():
    native=load(native_path);runs=native.get('runs',[])
    lines += ['', '## Дополнительные startup-проверки 26.3','',
        '| Среда | Сохранено попыток | Строгий functional PASS | Нормальные client/server exits | Native access violation |',
        '|---|---|---|---|---|']
    for environment in ['Windows','native-WSL']:
        cases=[case for case in runs if case['environment']==environment]
        lines.append('| %s | %d | %d | %d | %d |'%(environment,len(cases),sum(c['strict_functional_pass'] for c in cases),sum(c['normal_exit'] for c in cases),sum(c['windows_native_access_violation'] for c in cases)))
    lines += ['', 'План: четыре обязательных Linux режима и ещё два полных свежих production REST-запуска. Шесть подтверждённых Linux запусков: '+('PASS' if native.get('linux_six_verified_fresh_production_runs') else 'NOT VERIFIED')+'. Старые Wayland/process-only результаты остаются в числе попыток, но не в строгом headless PASS. Полные пути и exit verdicts: [native-startup.json](results/phase5/native-startup.json). Native crash status остаётся OPEN независимо от числа успешных повторов.']
lines += ['', '## Linux / CI','',
 'По прямому решению пользователя проверки выполняются только локально в Windows/WSL. Передача SDK на внешний SSH-хост отклонена и не выполнялась. Исторический `phase5_remote.py` запрещает remote actions. До этого сохранялась только подготовка публичного Docker image без кода SDK.','',
 'GitHub Actions template: `ci/phase5/matrix.yml`. Отдельные matrix jobs compile/unit → test Maven publication → consumer/packaging/API → network/production runtime → summary. Все 13 целей читаются из targets.json. Actions закреплены SHA, JDK — версиями и SHA256. Отдельный namespace github.run_id исключает использование исторических PASS из checkout. Workflow пока изолирован от release `.github/workflows`; hosted GitHub Actions запуск **NOT RUN**.','',
 'Локальная WSL подготовка: свежие source/home/Gradle state на нативной Linux FS. Manifests `local-environment/wsl-native-inputs.json` и при наличии `wsl-native-b-inputs.json` фиксируют хэшированные dependency/raw Minecraft inputs. При использовании native-b он имеет отдельные HOME/Maven/Gradle/project outputs, игровые порты и Xvfb display; immutable dependencies могут использовать hard links. Готовые классы/JAR нового SDK и сгенерированные Loom Minecraft outputs не переносились; legacy SDK JAR/probe намеренно сохранены как compatibility fixtures. Assets перенесены с /mnt/c на native Linux FS с проверкой SHA; неверный prefixed index исключён. Первая медленная попытка на /mnt/c сохранена отдельно. Каждый task cgroup: 6 GiB RAM, 1 GiB swap, 2 CPU, 2048 PIDs; глобальные настройки WSL не изменены. Полный Linux PASS target берётся из одного worker; неполные jobs не смешиваются.','',
 'WSL system dpkg оказался незавершённым; системная Java не ремонтировалась. Полные переносимые JDK с desktop JRE извлечены в `/var/tmp/fte-sdk-phase5-01a13db2/tools`. Логи содержат pins/SHA Ubuntu JDK 21.0.12.1/25.0.4.1, Xvfb и Python. Эти локальные JDK отличаются от Temurin pins hosted template; результаты сред не подменяются. Фактические Linux jobs: `results/phase5/wsl/`, а не публичная сборка Docker image.','',
 'Headless runtime дополнительно требует сохранённый успешный xdpyinfo receipt. Локальный WSL использует приватный mount namespace, официальный task-local xkbcomp и отдельный X11 socket; WSLg fallback отключён. Глобальные пакеты и JVM не изменены. Первые Java GLFW failures и прежние успешные Linux process runs без доказанного Xvfb сохранены; они не засчитываются как headless PASS. См. PHASE5_TEST_FIXES.md и local-environment/x11-display-probes.json.','',
 '## Неуспешные попытки','']
(ROOT/'results/phase5/failed-attempts.json').write_text(json.dumps(r['failed_attempts'],ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for a in r['failed_attempts']:
    error=str(a.get('error') or a['status'])
    lines.append('- `'+a['path'].replace('\\','/')+'`: '+error[:220]+('… (полный error в JSON)' if len(error)>220 else ''))
lines += ['', 'Исходные gradle/client/server/backend logs и JSON находятся рядом с каждой попыткой. Нехватка RAM и прерванный WSL daemon зафиксированы отдельно в `results/phase5/local-environment/resource-budget.json`. Таймауты повторяются без ослабления assertions. Удаляются только регенерируемые игровые inputs/fixture worlds после завершения процессов, с manifest в `local-environment/reclaimed-inputs`; логи, SDK artifacts и crash diagnostics сохраняются.','',
 '## Сохранённые продуктовые ограничения','',
 '- Bounded best effort: snapshots 64 с coalescing/eviction, важные события 256 FIFO с reject overflow, TTL 15/30 секунд. Бан/координаты могут потеряться при переполнении, истечении TTL, shutdown, смене игровой сессии или неоднозначной ошибке отправки. Подтверждения backend и replay не добавлены.',
 '- Один глобальный runtime: любой consumer может остановить его через shutdown(). Идентичная полная конфигурация разделяет runtime; конфликт ключа/настроек явно отклоняется. Будущие варианты владения: owner handle либо reference-counted leases, после отдельного решения API.',
 '- Jar-in-jar deduplication подтверждена для двух consumer-модов с одной проверяемой версией SDK. Старый бинарный API-клиент выполняется с новым SDK, но это не отдельная проверка разрешения Fabric Loader двух разных SDK JAR версий. Не следует устанавливать экспериментальный poc и release SDK одновременно; упаковка и mod ID окончательного релиза относятся к следующему этапу интеграции.',
 '- Первичный Builder и shutdown требуют клиентский поток. Вызывать Builder в клиентском Fabric entrypoint или через Minecraft.execute; API-ключ и конфигурация другого consumer незаметно не используются.',
 '- SessionFence отменяет неотправленные игровые payload предыдущей сессии. Это соответствует свежести snapshots; для исторических банов/координат означает возможную потерю при reconnect. Пересмотр политики важных событий требует отдельного продуктового решения.',
 '- Exactly-once без серверной идемпотентности не обещается.',
 '- Native startup 26.3 остаётся OPEN, включая ранее подтверждённый сбой без SDK. Успешные функциональные запуски не закрывают расследование. JVM/driver обходы в SDK не добавлялись.',
 '- На этапе 5 native crash повторился в Windows flags attempt-01: 0xC0000005, jvm.dll+0xA7D687. Report.wer и события сохранены, временный dump уже отсутствовал. См. NATIVE_CRASH_INVESTIGATION.md; последующий PASS не отменяет этот FAIL.',
 '- Реальный FunTime backend/сервер не использовался; protocol проверяется локальным fake backend и фактическими Fabric production callbacks. Production-ready пока не заявляется.','',
 '## Воспроизведение','',
 'Из `poc/multiversion` (в этой среде shell-команды выполняются через `rtk proxy`):','',
 '```text',
 'python phase5_build.py --targets all --kind compile',
 'python phase5_build.py --targets all --kind publish',
 'python phase5_build.py --targets all --kind consumer',
 'python phase5_verify.py --targets all --stage artifacts',
 'python phase5_api.py --targets all',
 'python prepare_phase4_tls.py',
 'python phase5_run.py --targets all --kind network',
 'python phase5_verify.py --targets all --stage network',
 'python phase5_run.py --targets all --kind runtime',
 'python phase5_verify.py --targets all --stage runtime',
 'python phase5_report.py',
 'python phase5_integrity.py',
 'python phase5_cross_os.py',
 'python phase5_perf.py',
 'python phase5_perf_control.py',
 'python phase5_headless_check.py',
 'python phase5_final_verify.py --workers-finished',
 'python phase5_manifest.py',
 'python phase5_manifest.py --verify',
 'python phase5_runtime_resume.py --targets all --min-free-mib 3072',
 '```','',
 'Повторный publish той же уже существующей Maven Local версии намеренно запрещён. Для воспроизведения использовать отдельный HOME/Maven repository или новую явно выбранную экспериментальную версию; существующие файлы не удалять. WSL: `phase5_wsl.py --action prepare`, затем `--action run --kind compile|publish|consumer|network|runtime --targets <MC>`. Linux bootstrap и отдельные CI stages: `ci/phase5/`.']
(ROOT/'PHASE5_RESULTS.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(overall,'Windows',sum(t['status']=='PASS' for t in r['targets'].values()),'/13 Linux',sum(t['status']=='PASS' for t in linux.values()),'/13',flush=True)
