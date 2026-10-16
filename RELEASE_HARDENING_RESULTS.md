# Release hardening — 2.0.0-rc.2

## Итог

Функциональные обязательные проверки: **PASS**. Публичная публикация, tag, GitHub
Release и merge не выполнены. Решение о выпуске остаётся за владельцем проекта.
Windows native crash 26.3 — **OPEN**. Дополнительный performance matched pair —
**NOT VERIFIED**; он не включён в PASS матрицы.

Проверенный commit: `fc9e8611601e08431ca14e97511f85a76dbd99bc`. SDK ownership-код впервые зафиксирован в
`4a17019`; последующие изменения candidate fixtures не меняют его бинарные входы.
Ветка `integration/multiversion-rc`, main не изменена. rc.1 не перезаписывался.
Повторная проверка прежних manifests: 12563 файлов этапа 6 и
3443 файлов этапа 5, **0 несовпадений**.

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

Run [38876076261](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38876076261), attempt **1**, **18/18 jobs success**.
Каждая цель — свежий checkout, staging и Maven Local, без старых SDK artifacts.
Provenance сверяет commit/run/attempt/version и действительный pinned JDK runtime.
Aggregate sdkBuild/sdkStage без фильтра совпал по SHA-256 с независимой матрицей:
**52/52 файлов**. Два production Loader controls и required matrix — PASS.

История прогонов, включая отменённые:

- [38873844153](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38873844153), attempt 1, `4a17019d3d4a2cc24edbdf80ed86b9590096901d`: **cancelled**.
- [38874304432](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38874304432), attempt 1, `9f1697835ae774680a16d94383c090258f2e9ef1`: **cancelled**.
- [38876076261](https://github.com/Rizzener/FunTimeEventsSDK/actions/runs/38876076261), attempt 1, `fc9e8611601e08431ca14e97511f85a76dbd99bc`: **success**.

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
| 1.21.4 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.5 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.6 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.7 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.8 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.9 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.10 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 1.21.11 | 21 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.1 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.1.2 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.2 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| 26.3 | 25 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

Всего **54 production starts/54 PASS**, 1134 JOIN,
1134 DISCONNECT, 2160 callbacks, 908 payload.
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

Будущие координаты: `com.github.Rizzener.FunTimeEventsSDK:mc<точная-версия>:2.0.0-rc.2`.
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
| 1.21.4 | 45.27–53.31 | 10.28 | 1387–1477 |
| 1.21.5 | 42.52–50.70 | 8.35 | 1386–1478 |
| 1.21.6 | 47.94–55.29 | 10.67 | 1387–1437 |
| 1.21.7 | 50.22–56.38 | 21.90 | 1386–1439 |
| 1.21.8 | 42.61–49.88 | 11.70 | 1388–1433 |
| 1.21.9 | 43.86–52.42 | 10.68 | 1373–1464 |
| 1.21.10 | 47.09–52.52 | 11.71 | 1376–1464 |
| 1.21.11 | 48.21–52.26 | 12.13 | 1306–1419 |
| 26.1 | 45.26–52.80 | 11.38 | 1219–1292 |
| 26.1.1 | 48.18–53.95 | 14.66 | 1216–1307 |
| 26.1.2 | 40.14–46.30 | 6.33 | 1216–1263 |
| 26.2 | 48.46–54.86 | 11.93 | 1219–1269 |
| 26.3 | 45.94–51.35 | 10.33 | 1273–1319 |

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

Fresh evidence: `results/release-hardening/hosted/38876076261/`, локальные попытки —
`results/release-hardening/local/`. Candidate Maven: `results/release-hardening/candidate/2.0.0-rc.2/`.
Все 17 selected raw Actions ZIP сверены с GitHub artifact digest.
Durable local archive: **PASS**, receipt —
`results/release-hardening/archive-receipt.json`, каталог `release-evidence/2.0.0-rc.2/`.
Он отделён от временного Actions storage. Off-device backup **NOT RUN**: внешнее
место хранения не согласовано. Локальный архив не защищает от потери этого диска.
Исторические измерения и неудачные matched-pair attempts дополнительно сохранены
с SHA manifest в `release-evidence/2.0.0-rc.2/historical-performance/` (204 файла).

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
Xvfb wrapper — `ci/phase5/x11-runtime.sh`. Сборка на Gradle 9.6.1, Loom
1.17.21, Loader 0.19.5, pinned Temurin 21.0.9+10/25.0.4+7, FAPI из targets.json.

Сбор evidence: `python tools/hardening_local.py tools/github_phase6.py --run-id 38876076261 --collect`.
Отчёт: `python tools/hardening_report.py --run-id 38876076261`. Архив:
`python tools/archive_release_evidence.py --run-id 38876076261` (однократно).
Seal: `python tools/hardening_report.py --run-id 38876076261 --seal`.

## SHA-256 production SDK JAR

| Target | SHA-256 |
|---|---|
| 1.21.4 | `be1f8fad4f8b3a5fd85e117c1bf6e13de25bf126e126d3f2fe72f9c8be5b1ceb` |
| 1.21.5 | `a13adbb474b13ecbecddef1dbd0fceb118a1aa403fdc0430abb88f2b3edf2b6a` |
| 1.21.6 | `7753ce75c17d34a6897015a78efcd3ca49364d2fcc759203484adb7c1aa44d42` |
| 1.21.7 | `18072a179b2d6068946c492bf9860320e7b2d5f135313cf17c1d1b2aceb4887b` |
| 1.21.8 | `d47d3b1be354edb5806dbdb4e8a27df954446d36422db625d24304a3fa2d3e33` |
| 1.21.9 | `c69c4e98058dabbf048622ecbb723da9ad5aaf6b9b56ad1c21e39748775f6236` |
| 1.21.10 | `f05f9235a71235e47a4114c48da5df700298609ed8576496b4df177c45a59dd8` |
| 1.21.11 | `11f378dece48edb92561f722c2a7e64cd957c9ea451132da5003f23f6ae86715` |
| 26.1 | `081711984c1e2b2e7a671e47078d5dd591b258b29c5e748f618bc0118c907bac` |
| 26.1.1 | `298f7612ba18e122f7dd9cb9dd970d53c51c382aae7b3d5085b9fd83ffa93b90` |
| 26.1.2 | `dc2d14b1a89a4d3cbdbf3154a3b0dcb8bc134924014c30123739ba8d6a80b80b` |
| 26.2 | `493b42c73a640461f4eb1c8b9d5969e662aff48073066c7b204ce294f527edde` |
| 26.3 | `45acc304071c6534a2800b4d2aee0c5e5a159314b04d1bed73f490c430f91b35` |

## Оставшиеся ограничения и решение о выпуске

Native Windows issue OPEN; strict matched performance NOT VERIFIED; backend delivery
upgrade OPEN; только локальный durable archive, без внешней копии. Singleton принимает
только одинаковые конфигурации; legacy build callers требуют координации shutdown.
Фоновая ownership-операция ждёт client executor: нельзя блокировать его join такого
worker. JitPack actual publication/резолюция нового RC — NOT RUN по запрету публикации.

Рекомендуемый кандидат — 2.0.0-rc.2; rc.1 остаётся неизменным. Следующий шаг — отдельное
решение владельца о публичной публикации и допустимости перечисленных ограничений.
Смена Maven-канала, tag/Release и merge требуют отдельного разрешения.
