# PHASE 6 — integration and release candidate

**Состояние: VERIFIED_RC_NATIVE_OPEN.** Версия `2.0.0-rc.1`, group `com.github.Rizzener.FunTimeEventsSDK`.
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
| 1.21.4 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 1.21.5 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 1.21.6 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 1.21.7 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 1.21.8 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 1.21.9 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 1.21.10 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 1.21.11 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 26.1 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 26.1.1 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 26.1.2 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 26.2 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |
| 26.3 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | success |

`runtime PASS` требует четыре режима offline/REST/WSS/flags, настоящие production
Fabric callbacks, JOIN/DISCONNECT 21/21, 40 сообщений, 9 scheduled scans, goldens
всех семи payload, TAB/hover/scoreboard/boss bar/entities/Text Display/equipment/
chests/timers, late-disconnect stress, single nested SDK и old binary probe.
Linux требует реальный Xvfb/xdpyinfo; 26.3 дополнительно фактический SDL X11 marker
и два повторных REST запуска. Отсутствующая строка не считается проверенной.
Фактические суммарные счётчики выбранного attempt: 54 starts,
54 PASS, JOIN/DISCONNECT 1134/1134,
2160 callbacks, 908 tracker payloads.
Отдельные Captcha/GET/protocol-unit запросы не входят в счётчик tracker payloads.

Развёрнутая матрица игровых возможностей (PASS только при полном runtime PASS):

| MC | Ban + hover | Copper dungeon | Warden city | Hell boss bar | TAB | Mine | Event coordinates |
|---|---|---|---|---|---|---|---|
| 1.21.4 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.5 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.6 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.7 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.8 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.9 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.10 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.11 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.3 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

Сверяются все поля payload и декодированный wire JSON после разрешённой нормализации
динамического ISO8601 времени; patterns/зоны/интервалы сохранены source parity.
Raw HTTP/WS bytes сохраняются; равенство gzip byte streams и JSON whitespace
не объявляется результатом semantic JSON comparison. Headers/gzip/envelopes проверяются отдельно.
Тестовый интервал ускоряет сцены; это не изменение production defaults.

Дополнительные локальные Windows результаты:

- 1.21.4: offline=PASS, rest=PASS, ws=PASS, flags=PASS; artifacts/API/network=PASS,PASS,PASS.
- 26.3: offline=PASS, rest=PASS, ws=PASS, flags=PASS; artifacts/API/network=PASS,PASS,PASS.

## 4. Настоящие GitHub Actions

Основной полный run: [38844176709](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38844176709),
attempt `2`, SHA `959fc3327ad4d107f1b0dbac89a27271ed374dea`, status `completed`,
conclusion `success`.
Фиксированная [ссылка на attempt 2](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38844176709/attempts/2).

| Обязательный job | Result | Job ID / raw log |
|---|---|---|
| all-targets | success | [116794521863](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38844176709/job/116794521863) |
| loader-controls (26.3, 1.21.4) | success | [116800069454](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38844176709/job/116800069454) |
| loader-controls (1.21.4, 26.3) | success | [116800069541](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38844176709/job/116800069541) |
| SDK required matrix | success | [116800771809](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38844176709/job/116800771809) |

Предыдущие runs (их результаты не подставляются в итоговую матрицу):

- [38837157480](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38837157480), `6454e0a65c50277a426ba724598a9400d7c95482`, `cancelled`: Superseded by mandatory Loader, full public inventory and standalone consumer negative controls; no SDK source change.
- [38838359555](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38838359555), `43c0bdcf2c1cc53e7117049f22fdf58b06666081`, `cancelled`: JDK autodetection selected an unpinned system JDK; replaced with strict toolchains and aggregate Gradle verification. SDK source unchanged.
- [38839563028](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38839563028), `3e99652e0800a06b86cf31d9791630f331c074e3`, `cancelled`: 26.3 production offline and REST exceeded 600 seconds each; 12 targets PASS. Stop remaining repeated timeouts and preserve diagnostics; candidate not verified.
- [38842085209](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38842085209), `1d042951198523cf79e3713e78669df3f715d4b4`, `cancelled`: Pending run would repeat confirmed 26.3 Linux production timeouts; diagnose retained runtime logs before retrying. No SDK source change.
- [38843094208](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38843094208), `356f97d9e433d4dea034607eb46265aaae9f586e`, `cancelled`: Superseded by final 959fc33 run, which additionally verifies self-contained detached Maven consumers on all 13 targets. Production SDK input tree unchanged. Preserve completed fixtures; no earlier PASS fills final matrix.

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

Pinned: Gradle 9.6.1, Loom 1.17.21, Loader 0.19.5,
installer 1.1.2; Fabric API для каждой строки — targets.json.
Hosted JDKs: Temurin 21.0.9+10 и 25.0.4+7, archive SHA-256 в ci/phase5/jdks.json.
Ubuntu 24.04, Xvfb, software rendering. Action revisions закреплены SHA.

Evidence и staging свежие для run/attempt. CI не использует старый PoC PASS,
не скачивает ранее опубликованные SDK, проверяет commit/run/attempt provenance.
`SDK required matrix` требует успеха всех targets и обеих Loader control jobs;
missing/skipped/failure дают красный статус. Защита main не изменялась.
Raw hosted API metadata/log ZIP/artifacts: `results/phase6/hosted/`.
SHA-256 всех 17 выбранных artifact ZIP сверены с digest GitHub API:
`hosted-archive-digests.json`; старые attempt ZIP в эту проверку не подставляются.
Отдельный hosted job выполняет **один Gradle build всех 13 targets** без фильтра,
`javaToolchains sdkBuild sdkStage`. Сравнение с независимыми matrix outputs:
`PASS`, 52/52 файлов
JAR/sources/POM/module. Итоговый required status проверяет байтовое равенство.

## 5. Maven staging и release hygiene

Target GAV: `com.github.Rizzener.FunTimeEventsSDK:mc<MC>:2.0.0-rc.1`.
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
Выбранный комплект кандидата: `results/phase6/candidate/2.0.0-rc.1/`,
manifest `PASS`. Комплект создаётся только после
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
| 1.21.4 | offline | 2173 | 43.60 | 42.81 | 8.17 | 1490.9 |
| 1.21.4 | rest | 2162 | 45.94 | 49.20 | 19.37 | 1451.7 |
| 1.21.4 | ws | 2169 | 41.88 | 49.84 | 15.47 | 1447.1 |
| 1.21.4 | flags | 2168 | 41.95 | 49.91 | 18.87 | 1398.5 |
| 26.3 | offline | 2198 | 37.43 | 44.33 | 5.66 | 1349.0 |
| 26.3 | rest | 2224 | 41.46 | 43.89 | 4.93 | 1295.0 |
| 26.3 | ws | 2191 | 40.00 | 43.45 | 7.28 | 1358.0 |
| 26.3 | flags | 2218 | 36.84 | 43.67 | 5.55 | 1249.9 |

Дополнительная последовательная REST/26.3 пара: прежний принятый SDK и кандидат,
та же машина/JDK/сцены; включена уже существовавшая опциональная thread CPU диагностика.
Исторический baseline никогда не заполняет Phase6 compatibility cells.

| SDK | Result | Active ticks | Mean wall us | Max wall ms | Mean allocated bytes |
|---|---|---:|---:|---:|---:|
| historical | PASS | 2206 | 45.08 | 5.16 | 1352.8 |
| candidate | NOT VERIFIED | — | — | — | — |

Thread CPU записывается только для spans >=1ms, поэтому из этих данных нельзя получить
полное среднее CPU. Все spans и failed controls сохранены в `performance-controls/`.
На Windows этот счётчик квантуется: наблюдались 0 и 15.625ms, что не позволяет
интерпретировать его как точный CPU расход отдельного микросекундного callback.
Свежий historical REST mean 45.08us отличается от прежнего Phase5 41.46us при
том же старом SDK; кандидат в основном Windows прогоне дал 43.89us. Это показывает
вариацию окружения. Дополнительная matched пара **NOT VERIFIED** из-за трёх failed
candidate attempts; устойчивое отсутствие регрессии этой парой не доказано.
Сводный новый CSV `results/phase6/performance-integrated.csv`: 540 строк;
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
| 1.21.4 | `f3be0cb2802989f5b4e3650bb4932e56d886c6780f1a14cc3bc354540c6859f3` |
| 1.21.5 | `b392d4239cd544152ef772ecea2fb0564201b29488085a8db4abd5ed1e8ba4c9` |
| 1.21.6 | `d8c0b1158f8d7216b2c8efa16e7175ec62ffb52389012a09cd3becacb07b79b9` |
| 1.21.7 | `bc4e244f22b1d2084529bd19c2861980cb5eb09b2e15da466285c65f1d8b4c03` |
| 1.21.8 | `282c8bca7c2f7456c02dec23f19e1b023b63ce6215b884cabf7790213e1b2a76` |
| 1.21.9 | `471b05c3e7cd15e96b48a5a2f7d79eb188cc0c441644af224793a8fbe0ebc769` |
| 1.21.10 | `cc71d8b26100486158f198532740173aea0d03a8d61e03cb2e8cc61f00897a1c` |
| 1.21.11 | `e1897ef04d3f45caa759cbd4abac9b896bacd29b935e52435301ddf9ada9df30` |
| 26.1 | `e7a1c84280e0ec98d004c8be29ec95350f6c3116431cb925c57a1a95b32790cc` |
| 26.1.1 | `cf2649b4f1c7287f144029b61d962ef51076822ad70efca29f46a8527ab41a1e` |
| 26.1.2 | `83fdf768f332c5cc8f3389f936b13ee2fbd72b5ae31ff19ba3cef5e88f6538b1` |
| 26.2 | `449a366744a8c6b17c592af07398993aa70839ebdc6cdadabf0fa4df664bebf8` |
| 26.3 | `cb91eafab6ec46e704b2164aea020ef4cae6d8f749dbf76d9fc3389681438398` |

Дополнительные SHA sources/POM/module и JIJ comparisons находятся в artifacts.json
и release-artifacts.json каждой цели. Windows локальные hashes сохраняются отдельно;
они не подменяют выбранные hosted release artifacts.

## 11. Воспроизведение

```powershell
.\gradlew sdkBuild
.\gradlew sdkStage -Ptarget_versions=1.21.4
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
python tools/github_phase6.py --run-id 38844176709 --collect
python tools/github_phase6.py --run-id 38844176709 --collect-attempt 1
python tools/phase6_report.py --run-id 38844176709
python tools/phase6_report.py --run-id 38844176709 --seal
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
