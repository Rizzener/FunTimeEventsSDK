# Этап 4: публичный API и настоящие транспорты

Итоговый отчёт, 15 октября 2026. Область — только `poc/multiversion`, Minecraft
1.21.4/Java21 и 26.3/Java25. Release SDK и настоящие Maven-координаты не изменяются.
Нативная проблема 26.3 из NATIVE_CRASH_INVESTIGATION.md остаётся **OPEN**.

## 4A — публичный API / runtime

Перенесены настоящий `net.funtimeevents.api.FunTimeEventsAPI`, `FteConfig.Builder`,
все модели и FteLogger. Сигнатуры фасада/Builder/моделей сохранены. Проверенные
трекеры получают все исходные enable flags и interval; старый TrackerSliceAPI
сохранён только для regression fixtures этапа 3.

SDK lifetime отделён от world lifetime: один зарегистрированный Fabric bridge
может прикреплять новый runtime после shutdown; JOIN/DISCONNECT управляют только
трекерами/сессионным состоянием. CLIENT_STOPPING вызывает shutdown.

Явные изменения семантики:

- Одинаковая конфигурация возвращает singleton. Отличающаяся отклоняется
  IllegalStateException, без значений ключей в сообщении. Молчаливое принятие
  чужого API-ключа больше не допускается.
- Добавлен `FunTimeEventsAPI.shutdown()`, вызываемый на клиентском потоке;
  release не имел публичного shutdown.
- Новая инициализация через Builder тоже требует клиентский/Render thread:
  install может сразу присоединить runtime к уже открытому world и прочитать
  Minecraft. Fabric client entrypoint поддержан. Equal-config повторный Builder
  только возвращает готовый runtime и может вызываться с другого потока. Старый
  API не проверял ownership явно; consumer, впервые строивший SDK в worker,
  должен перенести build в client initialization/execute. Сигнатуры сохранены,
  это явное ограничение runtime-семантики ради корректного владения игровым state.
- Invalid URL/log level и отсутствующие обязательные параметры отклоняются до
  выделения ресурсов. Runtime публикуется одной volatile ссылкой после успешного
  attach; ошибка attach закрывает принадлежащие ему ресурсы.
- Offline использует безопасный sender, выполняющий ноль сетевых операций.

Исходный 0.0.7-phase4a сохранил провал startup-проверки на обеих целях:
isSameThread() ещё false в Fabric client entrypoint на настоящем Render thread.
В 0.0.7-phase4a.2 проверка также учитывает RenderSystem.isOnRenderThread().
Это проверка реального потока, не сравнение строкового имени и не reflection.
Оригинальные неуспешные попытки сохранены в results/phase4/4A.

Проверка public descriptors 30 facade/config/model classes и компиляция старого
source против обоих новых JAR: PASS. Старый бинарный consumer компилируется против
настоящего `fte-api-1.0.5-SNAPSHOT.jar`; исходный JAR не изменяется.

Фактические проверки 4A:

| Проверка | 1.21.4 | 26.3 |
| --- | --- | --- |
| Production offline, 21 JOIN/21 DISCONNECT, 40 реальных GAME/CHAT callbacks | PASS, attempt-02 | PASS, attempt-02 |
| Повторные подключения, context reset, singleton, отсутствие сетевых отправок | PASS | PASS |
| Старый бинарный consumer с новой библиотекой | PASS | PASS |
| Равная/конфликтующая конфигурация, rollback, shutdown и reinitialize | PASS | PASS |
| Два настоящих мода с одинаковым nested SDK, одна загруженная копия, явный конфликт ключей | PASS, api-controls/attempt-02 | PASS, api-controls/attempt-02 |
| Defaults, все disable flags, min/custom interval, disabled runtime без game reads | PASS, Java21 | PASS, Java25 |

Логи/JSON: `results/phase4/4A`; descriptor/source checks —
`results/phase4/compat/0.0.7-phase4a.2`. Scope binary compatibility: фасад, Config,
Builder/LogLevel и модели (30 классов), не внутренние старые Minecraft-dependent
конструкторы трекеров. Исторические результаты этапов 2/3 сохранены отдельно.

В первом двухмодовом fixture build обнаружен Gradle declaration bucket `include`
с canBeResolved=false. Исправление касается только peer fixture: SDK берётся через
отдельную detached resolvable configuration, затем вкладывается как nested JAR.
Также сохранён неудачный прямой PowerShell вызов Gradle с разбитым -P аргументом;
исполняемые Python runners передают аргументы списком через subprocess.

Отдельный control `4A/version-conflict/attempt-02`: exact зависимости двух модов
на разные версии nested SDK отклонены Loader **до entrypoint**, ожидаемый exit1.
Предыдущий attempt-01 увидел тот же отказ, но runner завис на GUI dialog до timeout;
его FAIL/логи/очистка принадлежащих runner процессов сохранены. Повтор использует
headless только для этого намеренно ошибочного Loader control, не для game tests.

## 4B — паритет транспортов

На стадии 4B перенос использовал release ApiClient/RelayClient/RelayCache/SseCache без
сетевых исправлений; добавлен только close ApiClient для rollback/shutdown.
Исходные версии сохранены в fixtures/history/phase4-original. HTTP/WS/SSE fixture
поднимается только на loopback; HTTPS/WSS используют отдельный тестовый truststore,
без изменения системного trust и без отключения hostname verification.

Версия baseline: `net.funtimeevents.poc:mc<version>:0.0.8-phase4b`.
Проверка импорта: **29/29 PASS** — четыре transport sources (единственное
добавление ApiClient.close исключено из сравнения) и 25 побайтовых model copies.
Сборка/публикация, два независимых consumers и упаковка: **44/44 PASS**.
Публичные descriptors и старый source: PASS для обоих артефактов.

Один и тот же NetworkParityCheck выполнен с настоящим release JAR, перенесённым
Java21 transport и перенесённым Java25 transport. Все три PASS: GET, typed
players/bans, captcha, семь POST, gzip/plain, WSS auth/text/snapshots, SSE, TTL5s и
реальный HTTP timeout примерно15s. **17/17** сравнений wire-format со старым SDK
прошли; raw gzip bytes и декодированные JSON сохранены. Это самостоятельные
transport tests, а не Minecraft production-проверки.

Первые production REST прогоны обнаружили коллизию fixture NPC NoPrefix с локальным
игроком: actual world snapshot показал выход за Copper zone; SDK правильно не
сканировал её. Phase4 scenes revision4 задают отдельную local position; позиции
NPC/chests, golden payload и принятые Phase3 fixtures не изменены. Первые WSS
прогоны обнаружили пропущенный default tracked_anarchies=0 в нашем SystemInfo
golden. Также исправлен writer неудачного результата при session=0: он сам
выбрасывал IndexOutOfBoundsException и скрывал исходную assertion в JSON (raw log
сохранил обе ошибки). Эти четыре неуспешных попытки не удалены.

Первый test truststore содержал только локальный CA, вызывая дополнительные
PKIX failures фоновой Mojang-auth внутри тестового JVM. Следующая fixture версия
добавляет локальный сертификат к копии стандартных JDK roots в trust-full.p12.
Системный trust и JDK cacerts не изменялись. Ранний trust.p12 сохранён в .evidence.

Диагностические проверки до исправлений повторили **настоящие старые проблемы**
и в release JAR, и в перенесённом transport:

| Наблюдение | Старый release / перенесённый baseline |
| --- | --- |
| HTTP JSON на вызывающем потоке | true / true |
| Сериализаций для 5000 snapshots без соединения | 5000 / 5000 |
| Явно закрыт не прерываемый InputStream при SseCache.stop | false / false |
| Новых SSE запросов после EOF | 1 / 1 |
| Читатель увидел частично опубликованный cache | true / true |
| Соединения после auth timeout + disconnect | 2 open, 1 close; одно осталось открытым / то же |

Отдельный реальный late-callback сценарий: первое соединение не получает auth_ok,
через30s открывается второе и получает snapshot; затем backend закрывает первое
кодом4001. Baseline обнуляет актуальное состояние и не доставляет последующий Ban
через новое соединение. Наблюдение сохранено в `4B/late/attempt-01`.
Это подтверждение дефекта, **не PASS исправления**. Протокол backend не менялся.

## 4C — исправления

Начат после успешных production-проверок 4A/4B. Кандидат
`net.funtimeevents.poc:mc<version>:0.0.9-phase4c` опубликован в Maven Local,
не в release repository. SDK JAR этой версии зафиксированы; повторная публикация
той же версии runner запрещает. **104/104** pure Java sources в опубликованных
sources JAR совпадают с проверяемыми исходниками. Minecraft-зависимые исходники
проверяются компиляцией/production runtime, а не этим сравнением: sources JAR
1.21.4 проходит namespace remapping.

### Изменения транспорта и состояния

| Исходная проблема | Реализация 4C / воспроизводимая проверка |
| --- | --- |
| JSON/gzip на caller | HTTP POST готовится на `FTE-HTTP-POST`; GET/captcha — на двух `FTE-HTTP-Request`. Probe списка фиксирует настоящий поток сериализации. Gzip failure injection: **0 HTTP requests**, в том числе captcha, plain fallback отсутствует. |
| Неограниченные очереди | Общая package-private `PayloadQueue`: 64 snapshots, latest по `(тип,server_type,server_id)`; 256 важных событий Ban/Coordinates FIFO. 10000 одинаковых snapshots → один последний; 257-е событие отклонено; после 8 событий допускается snapshot. |
| Бесконечно старые данные | TTL snapshots 15s, событий 30s. Устаревшие session tickets удаляются, в том числе без соединения. При переполнении snapshot вытесняет самый старый snapshot; важное событие не вытесняет принятое ранее событие. |
| GET/captcha backlog | Executor: 2 worker + 64 waiting. Отказ возвращает null и rate-limited warning; shutdown завершает pending futures. Это отдельная очередь от игровых POST. |
| Параллельные WS sends | Один writer, включая auth; следующий `sendText` только после завершения предыдущего future. Проверены maxPending=1, exceptional future, reconnect и отсутствие replay неоднозначно доставленного сообщения. |
| Старые WS callbacks | Connection identity на auth/snapshot/onClose/onError/watchdog/dispatch; parsing вне короткой publication lock. Поздние 4001/error/auth/snapshot не меняют актуальное соединение или кеш. |
| Auth timeout / shutdown leak | Handshake15s, auth30s, snapshot watchdog15s; cancel handshake, abort socket при timeout/shutdown. Поздний onOpen после shutdown тоже abort. Repeated connect не создаёт второй pipeline. |
| Повторное чтение прежнего SSE response | SDK использует `startWith(Supplier<CompletableFuture<HttpResponse<InputStream>>>)`: новый HTTP request после EOF/error/status503, reconnect5s. |
| Зависший SSE read | Явное close активного/ошибочного/позднего InputStream, cancel response future, interrupt reader; Run identity блокирует публикацию от предыдущего reader после restart. |
| Частичный публичный cache | Полное новое состояние и volatile publication одной ссылки. Некорректный JSON не стирает последний целый snapshot. Семь nested list getters шести response DTO возвращают defensive mutable copies. |
| Offline null sender | Безопасный `OfflineSender`, ноль I/O и отправок; проверено в production 4A. |
| Старые игровые payload | `SessionFence.Ticket` захватывается при offer; end session атомарно запрещает новый dispatch и отменяет in-flight futures. Worker не читает Minecraft/server context заново. |

Это минимальные локальные изменения двух клиентов, двух кешей и sender/session
инфраструктуры. Три платформенных контракта не расширены в систему адаптеров.
Minecraft version exceptions остались прежними: **две пары небольших helper files,
пять различающихся методов**; два mixin accessor общие. Новых версионных исключений
для Builder/HTTP/WSS/SSE не добавлено. Reflection для совместимости не применяется.

### Проверки 4C вне Minecraft

`verification/network` содержит исполняемые Java fixtures. `FakeSocketClient`
реализует стандартный JDK HttpClient/WebSocket для управляемых futures; дополнительно
проверяются настоящие HTTPS/WSS/SSE соединения с loopback backend.

| Группа | Java21 | Java25 |
| --- | --- | --- |
| Bounded queues, coalescing, FIFO, fairness, TTL | PASS | PASS |
| Session tickets, cancel/dispatch; 4 producers ×5000 offers и 1000 смен session | PASS | PASS |
| HTTP JSON вне caller, gzip fault, stale preparation не отправлена | PASS | PASS |
| 100 GET requests, лимиты 2+64, отмена futures/ресурсов при close | PASS | PASS |
| Последовательные WS futures, failure/no replay, старые callbacks | PASS | PASS |
| Auth timeout, поздний handshake/open, watchdog/shutdown | PASS | PASS |
| SSE новый запрос после EOF/503, explicit stream close | PASS | PASS |
| SSE late future/restart, multiline data и комментарии | PASS | PASS |
| Атомарность кешей, malformed JSON, defensive nested lists | PASS | PASS |
| Завершение принадлежащих SDK threads | PASS | PASS |

Итог **20/20 групп**, `4C/hardening/attempt-06/*-checks.json`.
Последний прогон также проверяет настоящий WSS reject4001: одно открытие/закрытие,
нет reconnect и application payload после отказа авторизации.
Тест atomic cache выполняет 120 обновлений при конкурентном чтении 256 элементов;
он проверяет целостность отдельного getter, не транзакцию нескольких getter calls.

`4C/parity/attempt-02`: один harness с old release/Java21/Java25 — PASS,
**17/17** сравнений JSON/routes/headers/gzip/plain/WS со старым SDK — PASS.
Дополнительно выполняются HTTP503, typed error response, invalid JSON и captcha
error checks. Реальный HTTP timeout остаётся около15s.

`4C/faults/attempt-01` сравнивает настоящий старый release и кандидат Java25:

| Измерение | Старый release | 4C |
| --- | --- | --- |
| HTTP serialization на caller | true | false (`FTE-HTTP-POST`) |
| Сериализации 5000 offline WS snapshots | 5000 | 0 |
| InputStream явно закрыт на stop | false | true |
| Partial cache publication наблюдалась | true | false |
| Не закрытые auth-timeout connections | 1 | 0 (2 open/2 close) |

В этом diagnostic probe старый `SseCache.start(future)` даёт один request и после
исправления: сохранённый бинарный entrypoint теперь **one-shot**. Future уже
выполненного запроса не позволяет создать новый запрос. Автоматический reconnect
работает через additive `startWith(factory)` и проверен отдельно. Пользователю
обычного Builder менять код не нужно; прямому пользователю транспортного класса
для reconnect нужен factory. Это явное изменение прежней ошибочной семантики.

`4C/late/attempt-01`: настоящий WSS auth timeout30s, reconnect и late close4001
первого соединения — новый Ban **доставлен**. В 4B он не доставлялся.

### Явные пределы доставки и совместимости

- Протокол, routes, заголовки, WS text envelopes/auth, JSON и gzip сохранены.
  Backend ACK/idempotency не добавлялись. Гарантии exactly-once нет.
- Очереди намеренно bounded best effort: overflow, TTL и shutdown могут отбросить
  payload. Overflow/failure логируются, удаления учитываются в queue counters;
  отдельного публичного API метрик нет. Лимит — по числу элементов, не по байтам
  произвольного вручную созданного DTO.
- Прямой пользователь ApiClient/RelayClient должен передавать принадлежащие
  payload списки без дальнейшей мутации: JSON готовится асинхронно. Внутренние
  трекеры создают отдельные списки для каждой отправки и их не переиспользуют.
  Это ограничение существовало у async WSS; теперь относится и к REST. Полной
  копии произвольного пользовательского object graph на Render thread нет.
- После dispatch уже переданные сети байты нельзя отозвать. SessionFence
  гарантирует запрет нового stale dispatch после end; cancel in-flight best effort.
  Неоднозначно завершённый WS send не replay, чтобы не создавать дубликаты.
- Глобальные backend GET/caches живут в меню между игровыми сессиями. Они не
  относятся к прошлому Minecraft world. Shutdown очищает их. Relay getSystemInfo
  по прежней семантике возвращает последний SystemInfo даже после TTL; списки
  events/mines/loot — пустые после5s без обновления.
- `volatile` публикует целые состояния; compound transitions защищены ownership,
  identity и короткими locks. Состояние Minecraft/трекеров остаётся на клиентском
  потоке, Netty только передаёт disconnect identity в существующий handoff.
- Поддержан **один глобальный runtime**. Равная полная конфигурация двух модов
  разделяет runtime. Разные keys/UA/flags/interval/mode отклоняются явно; для смены
  конфигурации владелец должен shutdown/build. Один мод может shutdown общий
  runtime: reference-counted ownership не добавлялся и не обещается.
- Сохранены все прежние public descriptors проверяемых **36 классов**: facade,
  config/Builder/LogLevel, модели, ApiClient/RelayClient/RelayCache/SseCache,
  PayloadSender/FteLogger. Source compilation — PASS для обеих целей; old binary
  probe выполняется в production. Внутренние Minecraft-dependent конструкторы
  старых трекеров не объявлены стабильным бинарным API.
- Additive API: shutdown, session-aware constructors, SSE startWith. Дополнительно
  принимается null optional GET map (старый transport возвращал null по ошибке).
  Malformed typed response по прежнему завершает future exceptionally. Логуровень
  применяется до initialize и восстанавливается при rollback; disabled trackers
  не получают лишний stop. Outbound models/regex/зоны/алгоритмы не менялись.

### Неуспешные попытки 4C

Все сохранены, успешные прогоны не заменяют их:

- `build/attempt-01`: SDK compilation/publication command0 PASS; consumer command1
  FAIL из-за shadowing lambda variable в **fixture**. Исправлен consumer fixture;
  production runners заново собрали оба independent consumers с тем же frozen SDK.
  Весь исходный build result остаётся FAIL.
- `hardening/attempt-01`: Java21 PASS, Java25 ложная assertion `NEW,NEW`, потому что
  fixture считала backend records предыдущего JDK с общим User-Agent. `OLD` не
  отправлялся. Fixture разделена UA `/21` и `/25`; attempts02–05 PASS.
- `parity/attempt-01`: упал настоящий старый release: ожидался лишь events cache,
  остальные читались до завершения его последовательного fill. Functional parity
  теперь ждёт все пять списков/info; отдельный fault probe сохраняет доказательство
  partial publication старого SDK. Повтор attempt02 PASS на всех трёх profiles.
- `26.3/attempt-02` (WSS): native `0xC0000005` до игрового JOIN при resource reload.
  Сохранены client/server logs, Windows events, fixture sources и JAR hashes.
  Статус native startup **OPEN**, отдельно от функционального паритета.
- `26.3/attempt-03` (WSS): Java assertion `Stale server state` на rapid session14;
  joins14/disconnects13 и handoff_empty=false. Netty может доставить DISCONNECT
  между END_TICK listener SDK и последующим listener consumer. Fixture ошибочно
  требовала обработку в том же тике. Теперь она оставляет notification в своей
  очереди и ждёт не более **двух тиков**, затем по прежнему требует полный reset,
  ровно один disconnect и пустой handoff. `disconnect_deferred_ticks` записывается
  отдельно. SDK, его queue и опубликованные артефакты ради этой гонки не менялись.

## Production-матрица настоящего SDK

Это production launch через Fabric Loader, **development=false**, SDK origin
**NESTED**. Два независимых Gradle consumer roots получают готовый Maven JAR;
peer — второй реальный mod JAR с такой же вложенной библиотекой. SDK classes
не подмешиваются с workspace classes. Сервер создаёт настоящие vanilla packets,
а local backend принимает реальный HTTP/WebSocket трафик; recording sink в 4B/4C
не подставляется.

| Стадия / режим | 1.21.4 | 26.3 |
| --- | --- | --- |
| 4A offline/public Builder | PASS attempt-02 | PASS attempt-02 |
| 4B REST + пять SSE streams | PASS attempt-03 | PASS attempt-03 |
| 4B WSS authentication/snapshots | PASS attempt-04 | PASS attempt-04 |
| 4C REST + пять SSE streams | PASS attempt-01 | PASS attempt-01 |
| 4C WSS authentication/snapshots | PASS attempt-02 | PASS attempt-04 |
| 4C individual disable flags, fresh SDK runtime per session | PASS attempt-03 | PASS attempt-05 |

Каждый обычный online PASS: **21 JOIN/21 DISCONNECT, 40 GAME/CHAT callbacks,
9 scheduler scans, 25 golden payload**. На 26.3 повторный WSS PASS не отменяет
предыдущий native FAIL. Для 4C проверены online init failure/rollback (транспорт
создан, Bootstrap attach отклонён, ресурсы закрыты), затем успешный Builder;
после финального shutdown **нет живых `FTE-*` threads**. Ожидание завершения threads
выполняет test worker, а не Render thread.

| Функция | Обычные REST/WSS прогоны на обеих версиях |
| --- | --- |
| BanTracker | 10 Ban: GAME/CHAT, root/sibling hover, fallback server ID, старые reason/end; callback ровно один на input |
| EventCoordinatesTracker | 3 payload: допустимый GAME; CHAT-only, отсутствие уровня, overflow отвергнуты по прежним правилам |
| DungeonTracker | 1 copper + 1 warden: Text Display, таймеры59/83/125, сундуки, экипировка, donate, invisibility и координатные фильтры |
| TabTracker | 7 payload: реальные player info packets, display prefixes, только допустимые игроки, cache reset между серверами |
| MineTracker | 1 payload: lobby/distance/boundary filtering |
| HellMapTracker | 2 payload: реальные boss bars 17→9 и удаление; прежний100-tick polling |
| ServerContext / lifecycle | Scoreboard IDs/host, non-FunTime/no-ID, rapid reconnect, Netty→Render handoff, пустое состояние после каждого leave |
| Public GET | fetchEvents/UserEvents/Mines/Players/Bans/CopperDungeon/WardenCity; query escaping и default event_type=user |
| Public caches | getEvents/UserEvents/Mines/CopperDungeons/WardenCities; REST SSE и WS snapshot; getSystemInfo только WSS |
| Captcha | REST при любом режиме, прежние base64 JSON/response types, gzip/plain |

JSON сравнивается с прежними golden (порядок элементов нормализован, поле `active`
проверяется как ISO8601). Phase4 arrival rows имеют `tick=-1` и
`recorded_at_backend=true`: сетевое время прибытия не выдаётся за исходный game tick.
Периодичность подтверждают scheduler counters, unchanged source checks и сохранённые
Phase3 send-tick проверки, а не догадка по backend timestamps.

Для индивидуальных flags выполнено: Ban off в session1, Coordinates off2,
Dungeon off3, Mine off4, TAB off5, Hell off7. После каждого shutdown публичные
кеши пусты; новый Builder использует новую конфигурацию. Получены ровно16 payload:
TAB6/Ban7/copper1/coordinates2, без warden/mine/hell; при Dungeon off entity visits0.
Остальные сессии продолжают проверять очистку и отсутствие повторных подписок.
Отключение каждого флага отдельно здесь намеренно требует SDK reinitialize;
обычный runtime через world reconnect не пересоздаётся.

## Измерения производительности

Raw массив `callback_performance`: `[count,total_ns,max_ns,total_allocated_bytes,
max_allocated_bytes]`. Это spans SDK между fixture listeners; fixture JSON/golden
comparison и administrative backend requests в них не включены. Аллокации измерены
JVM ThreadMXBean на клиентском потоке, не по всем сетевым worker threads.

| 4C production | SDK active tick, среднее / max, мкс | SDK idle tick, среднее, мкс | Idle allocated bytes |
| --- | --- | --- | --- |
| 1.21.4 REST attempt-01 | 40.71 / 7351.6 | 4.38 | 0 |
| 1.21.4 WSS attempt-02 | 40.49 / 7575.2 | 6.02 | 0 |
| 26.3 REST attempt-01 | 39.37 / 7876.0 | 4.53 | 0 |
| 26.3 WSS attempt-04 | 40.30 / 6547.1 | 4.63 | 0 |

Для контекста baseline4B active means: 1.21.4 REST49.74/WSS41.37мкс,
26.3 REST44.00/WSS39.09мкс. Это отдельные запуски с JIT/cold first scan и другими
фоновыми процессами, **не FPS benchmark и не доказательство ускорения на X%**.
Функциональные scans, парсинг и allocation payload остаются на Render, JSON/gzip
перенесены в workers. Средние Ban message spans REST уменьшились с431/562мкс до
206/195мкс в этих samples; единичные cold scan maxima достигают миллисекунд.
Вне активной dungeon zone, на non-FunTime/no-ID entity/text/block reads равны0.
Новых world snapshot objects каждый тик архитектура не создаёт; session ticket
меняется при begin/end, queue item создаётся при payload offer.

## Сохранённые материалы и воспроизведение

- [Общий итог](results/phase4/final-summary.json): независимые evidence assertions,
  все попытки, failures/native codes и raw performance references.
- [Maven artifacts + SHA](results/phase4/evidence-manifest.json): JAR, sources,
  POM/module для 4A/4B/4C, включая раннюю неуспешную 4A версию.
- `4C/candidate-source.zip` и соседний JSON: исходники SDK, fixtures, golden,
  build scripts и runners; исключены build/cache/game binaries/private TLS keys.
- `fixtures/binary-compat/legacy-sdk.jar`: неизменный старый release baseline,
  SHA `d64bba38f2171acc84bf51da8fd9f2691f6a6b76b9c274837c64e3a3380219e3`.
- `fixtures/data/phase4/backend.json`, `delivery-policy.json`, `tracker-sessions.json`:
  golden DTOs, delivery limits, реальные world scenes. Phase2/3 fixtures не заменены.
- `4A/4B/4C/<MC>/attempt-*`: client/server raw logs, result JSON, fixture sources,
  consumer/peer/server fixture JAR. Backend sibling directories содержат raw JSONL
  с headers, gzip bytes, WS frames, SSE opens и принятыми payload.
- `4B/4C/{parity,hardening,faults,late,config}`: standalone Java logs, backend wire
  records и JSON проверок. Intentional503/gzip/failed-send warnings в negative
  tests не замаскированы; failed test runs сохраняют FAIL.
- [Команды полного воспроизведения](README.md#воспроизведение-этапа-4).
  `phase4_build.py --stage c --version <NEW>` делает SDK Gradle build/publication
  и два independent consumer builds последовательно. Frozen версии не overwrite.
  Standalone network tests используют текущие target classes; 104-source equality
  связывает их с опубликованным кандидатом. Production использует только Maven JAR.

Исторические 4A/4B transport tests нельзя воспроизвести компиляцией текущих 4C
исходников под старым номером. Для них сохранены исходники и Maven artifacts;
`run_phase4_network` отказывается неверно маркировать отличающийся source baseline.
Старые production JAR доступны через restore_phase4_maven без повторной публикации.
Для transport 4B добавлен `--stage b --kind parity --frozen`: SHA-verified retained
JAR идёт первым в test classpath, текущий harness компилируется отдельно, CodeSource
показывает именно старый артефакт. `4B/parity/attempt-02` повторно прошёл с настоящим
release и двумя frozen 4B JAR; **17/17 wire comparisons PASS**. Для faults/late
доступен тот же режим. Классы SDK не заменяются test doubles или новым SDK source.

## Итог и оставшиеся риски

4A: публичный фасад/config/Builder интегрирован; намеренное изменение singleton
conflict policy описано выше. 4B: паритет исходного транспорта доказан до исправлений.
4C: обязательные NETWORK_REVIEW изменения реализованы и проверены отдельно.
Финальная матрица flags — PASS на обеих целях. Aggregate verifier:
**20/20, PASS_NATIVE_OPEN**. Оба полных independent consumer builds повторно
прошли в `4C/consumer-build/attempt-01` с frozen Maven JAR без SDK republication;
исходный C build failure сохранён. Artifact/namespace/JIJ проверки — **44/44**.

Всего сохранено **47 stage4 attempts, из них14 FAIL**, включая ошибки fixtures,
startup guard, intentional Loader control с GUI timeout и native crash. Причины
описаны по стадиям; запись FAIL не заменяется успешным повтором. Финальные шесть
успешных C production runs: **126 JOIN/126 DISCONNECT, 240 callbacks, 132 payload**
(четыре обычных по25, два flags по16), все с normal client/server exit и пустыми
SDK threads после shutdown. Это функциональный результат в рамках fixtures.

Frozen C SDK SHA256:

| Артефакт | SHA256 |
| --- | --- |
| mc1.21.4-0.0.9-phase4c.jar | `918f3077cbd24af3bdf0b3de592466ea7b67be18c87d2051f2426c531b3d764a` |
| mc26.3-0.0.9-phase4c.jar | `38136235a6a8d055f71e805e1f0bb3c587b0bf803d02943872395b1bb1aafea3` |

`release-isolation.json` подтверждает нулевой Git diff release paths и неизменный
SHA оригинального release JAR. Все изменения находятся в `poc/multiversion`.
После прогонов `cleanup.json` подтвердил: все10 fixture ports закрыты, процессов
Java с test flags этапов2/3/4 и native controls не осталось. Другие процессы не
останавливались. Gradle daemon не считается игровым runtime SDK.

Регрессии старых проверок: Phase2 source14/14, Phase3 source18/18; исторические
runtime evidence Phase2 22/22 обе цели и Phase3 44/44 во всех accepted successful
runs продолжают проходить independent verifiers. Current production повторно
проверяет функциональность всех трекеров с настоящим публичным SDK/transport.

Ограничения остаются: native startup26.3 **OPEN**; локальный fixture server/backend
не заменяет проверку настоящего FunTime и backend infrastructure, доверенной
подписанной player chat/auth и длительной работы в интернете. Доставка best effort,
границы очередей/TTL и in-flight cancellation описаны явно. Одновременные разные
SDK версии с exact consumer dependency проверены как Loader rejection до entrypoint;
автоматическая совместимость произвольных будущих SDK версий не обещается.

Проверены **только 1.21.4 и26.3**. Архитектура не объявлена production-ready,
остальные11 версий не подключались. Release SDK, его координаты, источники и
документация вне `poc/multiversion` не изменены.

## Документация используемых механизмов

[JDK WebSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/WebSocket.html)
определяет completion futures отправок;
[JDK HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html)
описывает shutdown/cancellation и необходимость освобождать streaming body;
[Fabric metadata](https://wiki.fabricmc.net/documentation:fabric_mod_json_spec)
и [Loom options](https://docs.fabricmc.net/develop/loom/options) — metadata/nested
jars и production configuration. Выводы о PASS/FAIL основаны на локальных логах,
а не на том, что возможность упомянута в документации.
