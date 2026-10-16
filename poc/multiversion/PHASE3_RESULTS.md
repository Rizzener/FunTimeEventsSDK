# Этап 3: перенос всех игровых трекеров

Дата проверки: **15 октября 2026**, Windows x86_64.
Итог: **функциональные проверки PASS; native startup 26.3 — отдельная открытая
проблема, воспроизведённая также без SDK**. Полный статус JSON:
`FUNCTIONAL_PASS_NATIVE_OPEN`.

Область: **только Minecraft 1.21.4 и 26.3**, recording sink, изолированный
`poc/multiversion`. Действующие release source/build/координаты не меняются.
Экспериментальные координаты: `net.funtimeevents.poc:mc<version>:0.0.6-phase3`.
Версия `0.0.5-phase3` сохранена как предыдущая итерация; её результаты не выдаются
за проверки последующего исправления handoff.

## Устройство

Сохранён один Gradle multi-project и два независимых Maven consumer builds.
Core содержит прежние payload-модели, PayloadSender, Tracker, Scheduler, BanTracker,
ServerContext и внутреннюю очередь lifecycle. Minecraft-зависимые сканеры находятся
в **одном общем наборе platform/common**, компилируемом для каждой цели. Они
используют прямые Mojmap API. Полные копии трекеров по версиям не создавались.

Не добавлялись world/HUD snapshot DTO, универсальный GameAccess или новый EventBus.
Сохранены три контракта этапа 2. К IncomingMessage добавлено isGame с default=true:
Ban получает GAME/CHAT, EventCoordinates — только GAME, как в release source.
Экспериментальный TrackerSliceAPI не заменяет публичный release Builder.

## Алгоритмы и проверка source

`phase3_port_spec.py` задаёт механическое преобразование release Yarn source в общий
Mojmap source и сравнивает **целые пять файлов трекеров и PlayerNameUtil** с
результатом этого преобразования; в этих сравнениях нормализуются только окончания
строк. Это более строгая проверка, чем сравнение только regex. Двенадцать файлов
payload, PayloadSender, Tracker и Scheduler сравниваются побайтово. Итого **18 PASS**.
Отдельная проверка этапа 2 дала **14 PASS**, включая правила Ban/ServerContext,
BanPayload/GsonHolder и отсутствие reflection в платформенном коде.

Сохранены:

- Dungeon: обе зоны и границы XYZ, фильтр локального игрока до сканирования,
  экипировка, invisibility, ⚡-фильтр, 128-entry/30s donate cache, TIME_PATTERN.matches,
  dy=1..3, chest/barrel/trapped chest, putIfAbsent и финальная фильтрация сундуков.
- Hell: regex.find, только minecraft:nether-event, первый подходящий boss bar,
  интервал **100 активных клиентских тиков**, сброс счётчика при start.
- Mine: minecraft:lobby, BlockPos и проверка XZ, центр (-75.5,5.5), радиусы 40/15,
  Y не фильтруется; timestamp один на scan.
- Tab: прежний ⚡-фильтр и общий timestamp; как в TrackerManager, нерешённый
  serverId=-1 сам по себе не запрещает TAB payload.
- EventCoordinates: прежние три regex, GAME-only, проверка полноты и обработка
  NumberFormatException; неполные/overflow координаты не отправляются.
- Dungeon/Mine/TAB: прежний Scheduler, default **200 тиков**; интервал не ускорялся
  ради runtime тестов. Ban/ServerContext сохраняют правила второго этапа.

## Явные изменения и границы паритета

1. Подписки вынесены из конструкторов в единственный платформенный listener.
   Hell.clientTick вызывается им каждый тик; остальные scan — прежним Scheduler.
2. PlayerNameUtil и Dungeon donate cache очищаются при смене сессии. Release
   сохранял их до TTL, что могло переносить старый prefix на тот же UUID нового
   сервера. Алгоритм вычисления prefix и TTL внутри сессии не менялись.
3. Приватные Minecraft данные читаются через Mixin accessor/invoker. Это прямой
   доступ к реальному HUD/TextDisplay, без reflection/fallback на пустые данные.
4. TrackLog в эксперименте использует SLF4J с прежним тегом TRACK. Публичная
   конфигурация логирования и весь публичный API пока не переносились.
5. Метрики scan включаются только флагом fixture. В выключенном состоянии они
   не создают snapshot и не читают часы. Включённые измерения содержат стоимость
   recording sink и логирования; это не чистый FPS benchmark.
6. Новый JOIN другого handler завершает ещё активную предыдущую сессию, даже
   если её Netty DISCONNECT ещё не доставлен. Расширенный стресс сначала обнаружил
   старое состояние (joins=1, disconnects=0, serverId=104), после чего это исправлено
   в 0.0.6-phase3. Поздний callback старого handler теперь не сбрасывает новую
   сессию. Дубликат JOIN того же handler остаётся idempotent.

Сохранён прежний порядок: Dungeon получает текст из всех загруженных сущностей
после проверки зоны локального игрока и лишь затем фильтрует позиции сундуков.
Потенциальное улучшение предварительной фильтрации здесь не реализовывалось:
оно способно изменить putIfAbsent для голограмм на границе зоны. Вместо скрытой
оптимизации фиксируются entity visits / TextDisplay reads / block reads.

## Minecraft API и исключения

SDK: **две пары файлов, пять различающихся методов**:

| Метод | 1.21.4 | 26.3 |
| --- | --- | --- |
| HoverAccess.value | getValue(SHOW_TEXT) | HoverEvent.ShowText.value |
| VersionAccess.profileName | GameProfile.getName | GameProfile.name |
| VersionAccess.profileId | GameProfile.getId | GameProfile.id |
| VersionAccess.dimension | ResourceKey.location | ResourceKey.identifier |
| VersionAccess.bossOverlay | Gui.getBossOverlay | Gui.hud.getBossOverlay |

Дополнительно **два общих**, не версионных Mixin interfaces: BossHealthOverlay.events
и TextDisplay.getText. Метод getText приватный в обеих фактически используемых
версиях: старый подход через public getMethod не является надёжным доступом.
Имена, сигнатуры и видимость проверены javap фактических Minecraft JAR, а затем
проверяются production-запуском remapped/unobfuscated JAR.

Тестовый сервер имеет собственные исключения: FixtureVersion dimension,
EntityType → EntityTypes для TextDisplay/ArmorStand и UUID-параметр ServerBossEvent;
а также уже существующую пару FixtureHover. Они не попадают в SDK. Собственные
общие server mixins нужны только для заполнения deterministic TAB packet и
TextDisplay; это не новые платформенные контракты.

## Доставка fixtures

Локальный настоящий dedicated Fabric server передаёт стандартные TCP пакеты.
Два настоящих client production JAR загружают SDK из Maven dependency как nested
JAR. Данные читаются из настоящих ClientLevel, players, scoreboard, HUD и Component.
Никакие tracker.tick/handleMessage и message event invokers из consumer не вызываются.

NPC fixtures — детерминированные offline TAB/player/equipment пакеты, созданные
серверным тестовым модом; в клиенте возникают реальные PlayerInfo и RemotePlayer.
NPC не являются отдельными аутентифицированными пользователями. TextDisplay,
ArmorStand, блоки-контейнеры и boss bars создаются реальными серверными объектами.
Datapack добавляет настоящие измерения minecraft:lobby и minecraft:nether-event.

Адрес соединения — loopback, ServerData.ip — логический адрес из fixture, как на
этапе 2. Matcher FunTime не изменён, hosts/DNS не редактируются. Отдельный случай
example.invalid проверяет отказ всех трекеров по домену.

Финальная **ревизия fixtures 3** создаёт отдельный мир для каждого запуска, оставляя
неудачные миры для диагностики. Двести ArmorStand стоят в восьми блоках от игрока;
ещё 16 TextDisplay содержат не таймеры. Сценарий дополнительно содержит полезные,
дублирующиеся, невалидные и находящиеся за границей зоны голограммы.

Подготовка мира/teleport выполняется на первом END_SERVER_TICK после JOIN.
В фактическом Fabric networking API 26.3 серверный JOIN вызывается **внутри**
placeNewPlayer до завершения vanilla placement; teleport прямо из этого callback
приводил к duplicate UUID и DistanceManager NPE в тестовом сервере. Это исправление
fixture, не изменение SDK. Перед kick сервер ждёт `phase3 endseen`, отправленный
consumer после реального GAME callback END: фиксированная задержка в два тика
не гарантировала доставку перед очисткой vanilla client task queue.

## Матрица сценариев

| Сессия | Что проверяется |
| --- | --- |
| copper | Пограничные XZ, три типа контейнеров, таймеры dy=1/2/3, duplicate hologram, invalid 1:2, gear, ⚡, TAB, бан hover, координаты |
| outside-copper | Локальный игрок за границей; Dungeon не перебирает entities; TAB работает |
| warden | Вторая зона, invisibility, изменённый prefix того же UUID, экипировка и контейнеры |
| lobby | Радиус mine 15, local trigger 40, Y не влияет, TAB-only игрок не становится world player |
| lobby-outside | Локальный игрок за trigger radius: Mine не отправляет |
| wrong-dimension | Валидные boss bar/player данные в overworld не включают Mine/Hell |
| hell | Реальный boss bar 17 → 9 → remove; отправки на 100/200, отсутствие на 300 |
| non-funtime | Никакой отправки при валидных игровых данных на другом адресе |
| unresolved | Прежняя TAB семантика serverId=-1, Ban hover=77 разрешён, incomplete coordinates отклонены |
| rapid-1..12 | Быстрые повторные JOIN/DISCONNECT; отсутствие накопленных данных/повторных listeners |

На каждом выходе проверяются очищенные serverId/IP/onFuntime и отсутствие отправок
между сессиями. JSON сравнивается с независимым golden fixture. Только active
timestamp нормализуется после проверки ISO8601 и принадлежности текущей сессии;
неопределённый порядок players/chests канонизируется. Поля/значения не удаляются.

## Стресс lifecycle

Тот же DisconnectHandoff, который входит в production JAR, проверяется core-тестом:
10 001 соединение, дубликаты и запоздалые records после нового JOIN, 4 конкурентных
producer потока / 200 000 offers, 220 001 stale/duplicate notifications. Проверяется
опустошение очереди и отсутствие отключения новой сессии старым handler.
Один миллион пустых drain после прогрева: **0 выделенных байт в выполненном прогоне**.
Это синтетическая проверка handoff; реальные быстрые подключения проверяются
отдельно через Fabric, без выдачи синтетического события за сетевое.

Дополнительный regression test использует настоящий BanSliceRuntime и проверяет
порядок `JOIN old → JOIN new → поздний DISCONNECT old`: счётчики 2/1, новый адрес,
serverId=-1 до нового sidebar, новая сессия остаётся активной. Вариант до исправления
завершился assertion failure; raw log сохранён. Один и тот же тест запускается
на Java 21 и Java 25. Это не ручная подмена Minecraft данных в production fixtures.

## Фактические результаты

### Сборка, Maven и загрузка

| Проверка | Minecraft 1.21.4 | Minecraft 26.3 |
| --- | --- | --- |
| Общий core | Java 21, class major 65 | Тот же Java 21 core, class major 65 |
| Платформа | Java 21, class major 65 | Java 25, class major 69 |
| Minecraft source namespace | Mojang official mappings | Unobfuscated official |
| Публикуемая задача | remapJar | jar |
| Production runtime namespace | intermediary | official |
| Maven Local artifact | net.funtimeevents.poc:mc1.21.4:0.0.6-phase3 | net.funtimeevents.poc:mc26.3:0.0.6-phase3 |
| Независимый consumer build | PASS | PASS |
| SDK внутри consumer | Один nested JAR, origin=NESTED | Один nested JAR, origin=NESTED |
| Fabric development environment | false | false |
| Core / трекеры / helpers | По одной копии в SDK | По одной копии в SDK |
| POM/Gradle metadata | Нет транзитивного core | Нет транзитивного core |
| fabric.mod.json | minecraft =1.21.4, java >=21, client | minecraft =26.3, java >=25, client |
| Accessor/invoker mixins | Оба применились в production | Оба применились в production |

Gradle **9.6.1**, Loom **1.17.21**, Loader **0.19.5**, installer **1.1.2**;
Fabric API **0.113.0+1.21.4** / **0.163.0+26.3**. Gradle JVM — Java 25;
toolchains задают отдельно bytecode каждой цели. Последние production clients:
Azul **21.0.9+10-LTS** / Temurin **25.0.4+7-LTS**. Один дополнительный полный
успех 26.3 получен на Azul 25.0.1; он отмечен отдельно от default runtime.

Общий [artifact checker](results/phase3-artifact-checks.json): **44/44 PASS**.
Дополнительные проверки новой упаковки: **7/7 на каждую цель**, включая namespace
новых классов, два общих mixins, отсутствие fixtures/сетевых клиентов в SDK и
однократное включение core. Consumer использует `modImplementation` и `include`:
первый даёт compile/runtime dependency, второй физически вкладывает SDK в JAR.
Fabric API поставляется отдельно, не включается в SDK.

Размер/SHA256 обоих проверенных Maven JAR и хеши исходников сохранены в
[evidence-manifest.json](results/phase3/evidence-manifest.json). SDK 0.0.6 после
публикации не переписывался; изменения следующих попыток касались fixtures,
consumer-проверок и диагностики.

### Production-матрица трекеров

Все строки ниже проверены **на обеих версиях** через реальные сетевые пакеты,
Minecraft/Fabric callbacks и опубликованный SDK. Числа payload — за один полный
прогон из 21 сессии; это сравнение всех полей с golden, а не только счётчиков.

| Компонент | 1.21.4 | 26.3 | Payload и проверенное поведение |
| --- | --- | --- | --- |
| DungeonTracker | PASS | PASS | 1 Copper + 1 Warden; один подходящий игрок в каждой зоне; diamond helmet, netherite chestplate, none leggings, leather boots; invisibility false/true; по три контейнера и time_left 83/125/59 |
| TextDisplay / контейнеры | PASS | PASS | Прямой instanceof и invoker, реальные chest/barrel/trapped chest, dy=1/2/3; невалидный 1:2, дубликат и сундук за границей не добавляются |
| HellMapTracker | PASS | PASS | 2 HellMapPayload: mobs_count 17 и 9; чтение настоящего HUD map; после удаления boss bar нет третьей отправки |
| TabTracker | PASS | PASS | 7 TabPlayersPayload; ⚡-фильтр; TAB-only присутствует; prefix того же UUID меняется между сессиями; сохранён server_id=-1 |
| MineTracker | PASS | PASS | 1 MinePlayersAroundPayload; Near и Boundary включены, Far и плохой prefix исключены; большой Y не исключает; local вне radius40 и неверное измерение запрещают отправку |
| EventCoordinatesTracker | PASS | PASS | 3 EventCoordinatesPayload; прежние event/level и x=-123,y=64,z=456; неполные/overflow данные и CHAT-канал не дают payload |
| BanTracker, regression | PASS | PASS | 10 BanPayload; root/sibling hover, fallback server ID, причина/срок, бессрочный бан с пустой причиной, GAME и profileless CHAT |
| ServerContext | PASS | PASS | Реальный sidebar, FunTime address matcher, unknown ID и reset на каждом выходе; example.invalid не даёт отправок |
| Lifecycle всех трекеров | PASS | PASS | 21 start/stop на трекер, один runtime; 12 быстрых reconnect; после выхода состояние очищено, очередь пуста, вне сессии payload нет |

Итого **25 payload**, **40 fixture message callbacks** (30 GAME + 10 CHAT),
**21 JOIN / 21 DISCONNECT** за прогон. Server/client сообщения сопоставляются по
fixture ID, включая hover; повторы callback отклоняются. GAME-маркеры управления
сценарием считаются отдельно от этих 40 сообщений.

### Повторные запуски

| Версия | Финальные fixtures, revision 3 | Результат |
| --- | --- | --- |
| 1.21.4 | [attempt-04](results/phase3/1.21.4/attempt-04/result.json), [attempt-05](results/phase3/1.21.4/attempt-05/result.json) | 2/2 полных PASS, штатный выход client/server |
| 26.3 | [attempt-12](results/phase3/26.3/attempt-12/result.json), [attempt-13](results/phase3/26.3/attempt-13/result.json), [attempt-14](results/phase3/26.3/attempt-14/result.json) | 3/3 полных PASS на default Temurin, штатный выход client/server |

Каждый из пяти финальных прогонов прошёл **44 независимых утверждения**:
**220/220**, дополнительно по две проверки профилирования в последних прогонах
обеих целей. В них нет ошибок fixture physics, duplicate UUID или пропавшего END.
Все успешные запуски SDK 0.0.6, включая предыдущие ревизии fixtures: **3** на
1.21.4 и **5** на 26.3. Успехи 0.0.5 сохраняются как история и не подтверждают
последующее исправление handoff. Полный список PASS/FAIL без исключения неудач —
[phase3-summary.json](results/phase3-summary.json).

Core-проверки на Java 21/25 и Scheduler завершились PASS; исходный провал late-JOIN
и последующий успех сохранены в
[failed regression](results/phase3/build/phase3-late-join-failed.txt) и
[финальном build log](results/phase3/build/phase3-fixture-final.txt).

## Производительность

Ниже — wall-clock время SDK scan из последних полных прогонов (1.21.4 attempt-05,
26.3 attempt-14), **среднее / максимум в мс**. Число вызовов включает ранние выходы
по измерению/зоне. Измерения содержат холодный JIT, логирование, recording sink и
возможное вытеснение потока; они не являются сравнительным FPS benchmark.

| Путь | Вызовов | 1.21.4, мс | 26.3, мс |
| --- | --- | --- | --- |
| Dungeon scan | 9 | 1.606 / 12.671 | 0.716 / 5.194 |
| Hell scan | 17 | 0.164 / 1.730 | 0.282 / 3.049 |
| TAB scan | 9 | 0.531 / 1.548 | 0.484 / 1.040 |
| Mine scan | 9 | 0.240 / 1.833 | 0.385 / 2.863 |
| SDK callback с сообщением о бане | 15 | 0.761 / 6.437 | 0.594 / 5.102 |
| SDK callback с сообщением о координатах | 20 | 0.383 / 3.372 | 0.226 / 1.922 |
| SDK idle tick | 202 / 245 | 0.004 / 0.034 | 0.005 / 0.248 |
| SDK active tick | 2174 / 2201 | 0.048 / 14.332 | 0.049 / 6.337 |

Сообщения измеряются между реальными Fabric listeners до/после SDK: это общий
путь Ban + Coordinates + context + запись результата, не эксклюзивное время
одного parser. Даже отклонённое сообщение входит в измерение своего типа.

ThreadMXBean consumer измерил выделения Java heap на клиентском потоке за тот же
SDK-интервал. В idle tick — **0 байт** на обеих версиях. В active tick суммарно
**3 498 776 / 3 092 056 байт** за 2174/2201 вызовов; при сообщениях о банах —
**329 448 / 296 624 байт**, о координатах — **353 776 / 317 368 байт** за весь
набор. В эти числа входит синхронная запись/сериализация recording sink и холодные
вызовы. Это не заявления о нулевых аллокациях парсинга, GC всей игры или native heap.
Все исходные [count,totalNs,maxNs,totalAllocatedBytes,maxAllocatedBytes] сохранены
в `runtime.callback_performance` JSON соответствующих attempts.

Сцены содержат более 200 реальных сущностей, включая нерелевантные:

| Dungeon traversal | 1.21.4 visits / TextDisplay / blocks | 26.3 visits / TextDisplay / blocks |
| --- | --- | --- |
| copper | 230 / 22 / 15 | 234 / 22 / 15 |
| warden | 226 / 21 / 12 | 227 / 21 / 12 |
| За зоной или на чужом домене | 0 / 0 / 0 | 0 / 0 / 0 |

Разница visits отражает фактические сущности клиентского мира. Scan выполняется
на 200-м тике; Hell — каждые 100 активных тиков (17 → 9 → отсутствие на 300).
Полного world snapshot каждый тик нет. Пустой handoff также дал 0 heap-аллокаций
на 1 000 000 drain на каждом JDK. Тяжёлый холодный scan по-прежнему может занять
заметную долю кадра; исходный алгоритм не менялся ради улучшения цифр.

## Неудачные попытки и нативный сбой

Каждый запуск пишет новый results/phase3/<version>/attempt-NN. Неуспешные попытки
не перезаписываются. Сохраняются client/server stdout/stderr, JSON, новые hs_err/
crash-reports и Java crash events Windows за временное окно попытки.

| Попытки / журнал | Реальная ошибка | Решение / статус |
| --- | --- | --- |
| build-01..03 | Механический порт Registries, конфликт nested PlayerInfo с Mojmap PlayerInfo, перемещение boss HUD в Gui.hud; отдельные server fixture API | Прямые типизированные API, полное имя PlayerInfo и малый VersionAccess; raw compiler errors сохранены |
| late-join-failed | JOIN новой сессии до старого DISCONNECT оставлял serverId=104 и joins=1 | Исправлен production handoff в 0.0.6; регрессия PASS на J21/J25 |
| 26.3 attempt-08, 1.21.4 attempt-03 | ArmorStand отталкивали локального игрока за Copper zone; затем старые сущности сохранялись в повторно используемом мире | Отнесены decoys и введён свежий мир на каждый запуск; SDK правильно не сканировал вне зоны |
| 26.3 attempt-10/11 | END иногда терялся перед kick; teleport из mid-placement server JOIN вызывал duplicate UUID / DistanceManager NPE | ACK перед kick и подготовка на END_SERVER_TICK; изменения только fixtures, revision 3 |
| 26.3 attempt-03/05/06 | Native 0xC0000005, Temurin25, jvm.dll offsets A7D687 / 43C713 / A7D687, до JOIN | FAIL сохранены, отдельная диагностика |
| 26.3 attempt-09 | Native 0xC0000005, Azul25.0.1, jvm.dll+449D93, до JOIN | Смена поставщика не устранила сбой |

Все build raw logs — [results/phase3/build](results/phase3/build).
Историческая revision 2 server source/data сохранена в
[fixtures/history/revision-2](fixtures/history/revision-2). Perflib/JNA и offline
auth 401 встречаются также в успешных прогонах; они не объявлены причиной native
failure. Nonfatal сообщения flat-generator сохранены в исходных server logs.

Из этапа 2 остаётся код **0xC0000409**; в этапе 3 получен **0xC0000005**.
Не утверждается, что эти разные NTSTATUS имеют одну первопричину.

Сильнейший контроль этапа 3: native failure **воспроизведён без SDK и consumer**
(только Minecraft 26.3 + Loader + официальный Fabric API). Сохранён WER minidump:
PID19044, **Worker-Main-4**, **jvm.dll+0xA7D687**, 0xC0000005 — та же сигнатура,
что в attempt-03/06. SDK не является необходимым условием этого сбоя. Точная
native-функция/первопричина ещё не установлена; исправление не заявляется.
Три отдельных startup smoke под ProcDump прошли, два из трёх контролей без SDK
дожили до 45-секундного окна и были контролируемо остановлены. Эти результаты
не подменяют штатный выход и функциональные прогоны.

Подробная последовательность, raw logs, WER, SHA minidump и границы вывода —
[NATIVE_CRASH_INVESTIGATION.md](NATIVE_CRASH_INVESTIGATION.md).

## Сохранённые исполняемые проверки

| Файл / каталог | Назначение |
| --- | --- |
| phase3_port_spec.py | 18 проверок целых исходников и копий core |
| verify_source_parity.py | 14 regression guards Ban/ServerContext этапа 2 |
| core/src/verification | Ban, Scheduler, handoff concurrency и late JOIN на Java21/25 |
| fixtures/generate_phase3.py, fixtures/data/phase3 | Воспроизводимые 21 сессия и golden JSON |
| fixtures/server, consumers/common | Реальные sender packets/world и Fabric observers/recording sink |
| run_phase3.py | Production server/client orchestration; каждая попытка в новом каталоге |
| verify_phase3.py | Независимая сверка JSON, logs, Maven JAR, lifecycle, cadence и payload |
| verify_artifacts.py | Namespace/bytecode/metadata/POM/sources/jar-in-jar |
| collect_phase3_evidence.py | Копии build logs, SHA sources/JAR, сводка native контролей |
| run_native_diagnostics.py, native_dump.py, inspect_native_dump.py | Изолированные native controls и анализ сохранённого minidump |

Raw client/server stdout и stderr находятся рядом с каждым `result.json`.
Сводный JSON **не скрывает native FAIL**: `functional_status=PASS`,
`native_startup_status=OPEN_REPRODUCED`. Exit code 0 независимого verifier означает
прохождение функциональных критериев; он не объявляет native stability.

## Воспроизведение

JDK 21 и 25, Gradle JVM Java 25, установленный Python; из poc/multiversion:

```powershell
rtk proxy .\gradlew.bat pocBuild pocPublishLocal :mc1.21.4:remapServerFixtureJar :mc26.3:serverFixtureJar --console=plain --max-workers=2
rtk proxy python -X utf8 phase3_port_spec.py
rtk proxy python -X utf8 run_phase3.py --target all --repeats26 3
rtk proxy python -X utf8 verify_artifacts.py --version 0.0.6-phase3 --output results/phase3-artifact-checks.json
rtk proxy python -X utf8 verify_source_parity.py
rtk proxy python -X utf8 verify_phase3.py
rtk proxy python -X utf8 collect_phase3_evidence.py
```

Порты 28414/28633 должны быть свободны; сервер offline, только 127.0.0.1. Python
управляет процессами теста; сборка и публикация остаются средствами Gradle.
REST/WSS/SSE, release Builder и дополнительные Minecraft версии не подключаются.

## Границы результата

Функциональный паритет перенесённых игровых алгоритмов подтверждён указанными
source guards и production fixtures **для 1.21.4 и 26.3**. Оставшиеся 11 версий не
проверялись и не объявляются поддержанными. Запуск на настоящем FunTime, подписанный
player chat, отдельные авторизованные NPC-клиенты, настоящий backend, длительный
FPS/GC baseline старого SDK и native stability другого hardware в матрицу не входят.
Fixtures используют реальный profileless CHAT, который доставляется Fabric CHAT.

Модель потоков платформы: все игровые изменения принадлежат client thread;
Netty только добавляет identity handler в concurrent handoff queue. Одного volatile
для последовательности «сменить сессию, остановить трекеры, очистить context» было
бы недостаточно. Сетевые риски остаются в отдельном
[NETWORK_REVIEW.md](NETWORK_REVIEW.md); REST/WSS/SSE не рефакторились и не входят
в экспериментальные JAR. Проверка release paths не выявила изменений.

Архитектурного препятствия, требующего reflection или копий целых трекеров, не
обнаружено. Следующий этап публичного Builder/реальной сети можно проектировать
отдельно; текущий результат не выдаётся за готовый новый release SDK.

## Документация и проверенные исходники

Типизированный доступ к приватным членам соответствует
[Fabric Mixin accessors/invokers](https://docs.fabricmc.net/develop/mixins/accessors),
запуски готовых JAR используют
[Loom production run tasks](https://docs.fabricmc.net/develop/loom/production-run-tasks).
Место вызова серверного JOIN проверено в официальном
[Fabric networking 6.3.10 sources](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-networking-api-v1/6.3.10+5c1fee4d5d/fabric-networking-api-v1-6.3.10+5c1fee4d5d-sources.jar):
PlayerListMixin вызывает его у конструктора ClientboundPlayerAbilitiesPacket внутри
placeNewPlayer; клиентский ClientPacketListenerMixin — в RETURN handleLogin.
Сигнатуры Minecraft API дополнительно проверены непосредственно по JAR обеих целей
и выполнением mixins в production namespace.
