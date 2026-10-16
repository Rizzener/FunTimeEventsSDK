# Этап 2: BanTracker, ServerContext и реальные Fabric callbacks

Дата: 2026-10-15. Область: только Minecraft **1.21.4** и **26.3**.

## Область изменений

Эксперимент остаётся в `poc/multiversion`. Действующие `fte-api`, release build,
публичный API и release Maven-координаты не редактируются и не публикуются.
Экспериментальные артефакты: `net.funtimeevents.poc:mc<version>:0.0.4-phase2`.
Версия `0.0.1-poc` первого этапа не перезаписывалась. Первые попытки этапа 2
использовали `0.0.2-phase2`; варианты передачи lifecycle опубликованы отдельно
как `0.0.3-phase2` и `0.0.4-phase2`. Финальная проверяемая версия — `0.0.4-phase2`.

Перенесены только:

- настоящие regex и helper-методы BanTracker;
- преобразование обычного текста и hover;
- определение FunTime и server ID из sidebar;
- формирование прежнего `net.funtimeevents.model.BanPayload`;
- подписки JOIN/DISCONNECT/tick/GAME/CHAT и состояние игровой сессии.

Не переносились другие трекеры, конфигурация всего SDK, REST/WSS/SSE и их исправления.
`BanSliceAPI` — экспериментальный вход для fixture consumer, а не новая версия
публичного release API. Результат трекера принимается recording sink; сеть backend
этим этапом не проверяется и не подменяется утверждением о реальной отправке HTTP/WSS.

## Устройство среза

| Слой | Файлы / роль |
| --- | --- |
| Core | `core/.../poc/slice/BanTracker.java`, `ServerContext.java`, `BanSliceRuntime.java` |
| Минимальные контракты | `GameLifecycle` с listener JOIN/DISCONNECT/tick/message, `ServerAccess` с двумя методами, короткоживущее `IncomingMessage` |
| Общее чтение Minecraft | `platform/common/.../FabricBanPlatform.java`, `MinecraftText.java` |
| Исключение 1.21.4 | `platform/legacy/.../HoverAccess.java`: `getValue(SHOW_TEXT)` |
| Исключение 26.3 | `platform/modern/.../HoverAccess.java`: `HoverEvent.ShowText.value()` |
| Реальная модель | `core/.../model/BanPayload.java`, побайтовая копия release source |
| Fixtures | `fixtures/data/phase2/ban-sessions.json`, общий production server fixture и две небольшие фабрики hover |
| Consumer | `consumers/common/.../Phase2Consumer.java`, два независимых Maven consumer builds |

У core нет Minecraft/Fabric типов. Тексты, world и entities не сохраняются в
межпоточных очередях SDK. Только identity отключённого connection handler кратко
хранится в платформенной очереди до клиентского тика. Обёртка сообщения используется только внутри callback;
hover читается трекером после совпадения шаблона бана. Данные сервера публикуются
одной immutable записью, новая запись создаётся только при изменении значений.

Рефлексии, поиска классов по имени, ручного вызова event invokers и прямого вызова
парсера из runtime test driver нет. Это дополнительно проверяет `verify_source_parity.py`.

## Проверка неизменности правил и payload

`verify_source_parity.py` сравнивает с текущим неизменённым release source:

- все четыре объявления Pattern BanTracker;
- тела `parseReason`, `parseEnd`, `parseServerId`;
- оба шаблона ServerContext и значение server type `anarchy`;
- полные байты `BanPayload.java` и `GsonHolder.java`.

Результат: **14 PASS** (включая отсутствие рефлексии и прямого вызова парсеров/event
invokers в runtime fixtures). SHA-256 исходных release-файлов
записан в `results/phase2-source-parity.json`.

Core harness выполняет те же четыре fixture-сессии без Minecraft и проверяет
ожидаемые восемь JSON payload, idempotent JOIN/DISCONNECT и очистку состояния:
`[PHASE2-CORE] PASS sessions=4 payloads=8`.

Это не попытка запустить старый reflection-код SDK на новых mappings. Эталон здесь —
неизменённые правила, прежняя модель и явно заданные ожидаемые значения. Обработка
реальных Minecraft Component проверяется отдельно production-прогоном.

## Реальная цепочка доставки

```text
Fabric dedicated fixture server
  → sendSystemMessage / ClientboundDisguisedChatPacket
  → настоящие сетевые codecs и localhost TCP
  → Minecraft client packet handling
  → Fabric ClientReceiveMessageEvents.GAME / CHAT
  → общий платформенный адаптер → BanTracker
  → прежний BanPayload → recording sink → сравнение JSON
```

Тест не вызывает `ClientReceiveMessageEvents.*.invoker()` и не инжектирует сообщения
прямым вызовом обработчика SDK. Независимый observer consumer регистрируется в тех
же настоящих Fabric callbacks и фиксирует канал, ordinary text и hover. Лог сервера
отдельно содержит каждый отправленный fixture ID.

Production tasks получают готовые JAR. Consumer включает SDK через `include`;
SDK должен быть обнаружен Loader как `NESTED`, development mode должен быть false.
На сервер загружается отдельный server-fixture JAR, а не клиентский SDK JAR.
Старый server fixture тоже проходит remapping в Intermediary.

## Локальная адресация без подмены ServerContext

Тестовый consumer вызывает обычный `ConnectScreen.startConnecting`, передавая
фактический адрес `127.0.0.1:<port>` и логический `ServerData.ip` из fixtures.
Так проверяется настоящий matcher адреса, читаемого тем же способом, что в release SDK.
DNS/hosts и глобальные настройки компьютера не меняются. Соединений с FunTime нет.

Для negative case используется `example.invalid`. Никакого флага «считать любой
сервер FunTime» или специального разрешения localhost в production-коде нет.

Это контролируемая fixture-адресация, а не тест фактического DNS/прокси FunTime.

## Сценарии

| Сессия | Логический адрес | Sidebar | Проверка |
| --- | --- | --- | --- |
| 1 | fixture.funtime.su | сначала отсутствует, затем Анархия-104 | неизвестный ID не отправляется; root/sibling hover; отсутствие hover; настоящий CHAT |
| 2 | second.funtime.sh | сначала отсутствует, затем 205 → 206 | ID 104 не пережил reconnect; server=0 из hover использует sidebar; смена sidebar без reconnect |
| 3 | example.invalid | Анархия-306 | сообщение с валидным hover server=999 отклоняется из-за адреса |
| 4 | return.funtime.network | сначала отсутствует, затем Анархия-404 | возвращение с другого сервера; отсутствие старого ID; вложенные siblings hover |

Всего на одну версию: **13 fixture messages = 12 GAME + 1 CHAT**,
из них ожидаются **8 payload**, остальные 5 сообщений не должны сформировать payload.

| Fixture | Ожидаемый server_id | Причина / срок |
| --- | --- | --- |
| root-hover | 77 из hover | Читы / 2027-01-01 12:00 |
| sibling-hover | 104 из sidebar | Флуд / 7 дней |
| no-hover | 104 | пустая причина / Навсегда |
| profileless-chat | 104 | пустая причина / Навсегда |
| zero-server-fallback | 205 | Гриф / 3 дня |
| sidebar-change | 206 | пустая причина / Навсегда |
| deep-hover | 88 из hover | Читы / Навсегда |
| return-fallback | 404 | пустая причина / Навсегда |

Во всех восьми случаях дополнительно сравниваются `server_type`, `player_name`,
`reason`, `end`, набор и значения JSON-полей. Проверка не ограничивается числом payload.

Hover покрыт на root, sibling и sibling внутри sibling; проверены все четыре
варианта срока из исходного regex: До, Наказание, Окончание, Бан до.
CHAT fixture использует настоящий profileless/disguised chat. Подписанный player
chat с online-профилем этим offline fixture не проверяется.

## Lifecycle и обнаруженная проблема потоков

Первый runtime-прогон показал, что DISCONNECT приходит из Netty. Код первоначально
требовал клиентский поток, и переход с первой сессии не завершался. Официальный
исходник Fabric networking из использованного sources JAR подтвердил вызов
`handleDisconnect()` из `Connection.channelInactive`.

Рассмотрены варианты:

1. Добавить volatile ко всем полям и менять core из Netty — не обеспечивает
   согласованность состояний, оставляет Minecraft доступ вне владельца.
2. Добавить locks ко всем трекерам — лишняя сложность и риск блокировки клиента.
3. Нормализовать lifecycle на границе платформы — **выбрано**.

Первый вариант нормализации через `client.execute()` сработал на 1.21.4, но потерял
DISCONNECT на 26.3. В реальном запуске после Netty callback продолжался rendering,
но отложенная работа не выполнялась. Проверен байткод используемого Minecraft 26.3:
`Minecraft.disconnect(Screen, boolean, boolean)` вызывает `dropAllTasks()` перед
`ClientPacketListener.close()`. Таким образом, executor не гарантирует доставку
такого lifecycle события. Это существенное ограничение первоначального решения,
а не несовместимость Gradle multi-project.

Выбрана узкая `ConcurrentLinkedQueue<ClientPacketListener>` в платформе. Netty
только передаёт identity handler; на `END_CLIENT_TICK` очередь опустошается перед
вызовом core. Она также опустошается перед JOIN и сообщениями. Vanilla очистка
своего executor её не затрагивает. Запоздалое событие не сбрасывает новый handler.
JOIN, tick и сообщения проверяют клиентский поток. Подписки устанавливаются один
раз; повторная инициализация не создаёт второй runtime.

Здесь нет универсальной очереди payload, worker pool или нового EventBus. На пустом
тике — один `poll()`, без создания observation/snapshot; узел очереди создаётся
только при DISCONNECT. Сброс выполняется на следующем клиентском тике либо до
следующего JOIN/сообщения, а не синхронно в Netty callback. При остановленном
клиентском цикле такой сброс откладывается до его продолжения. Core по-прежнему
имеет одного владельца состояния; набор независимых volatile полей этого не заменяет.

В fixture consumer отдельно записаны исходный и обработавший потоки DISCONNECT.
Пример фактического результата 1.21.4: `Netty Client IO #0` → `Render thread`.
На каждом выходе проверяются serverId=-1, serverIp=null, onFuntime=false.

Это исправление необходимой платформенной границы вертикального среза,
а не изменение исходных REST/WSS/SSE клиентов. Их код не редактировался.

## Фактические результаты

Финальный прогон: **0.0.4-phase2**, Gradle **9.6.1**, Loom **1.17.21**,
Fabric Loader **0.19.5**, installer **1.1.2**. SDK, server fixture и consumer
скомпилированы отдельно для каждой цели. Fabric API: **0.113.0+1.21.4** и
**0.163.0+26.3** соответственно.

| Проверка | 1.21.4 | 26.3 |
| --- | --- | --- |
| Core harness (Java 21), компиляция SDK и fixture | PASS | PASS |
| Maven Local, sources, POM / metadata | PASS | PASS |
| Независимый consumer из Maven dependency | PASS | PASS |
| SDK упакован один раз как jar-in-jar, SHA совпадает с Maven | PASS | PASS |
| Production Java | 21 | 25 |
| Runtime namespace | intermediary (`remapJar`) | official (unobfuscated `jar`) |
| Loader origin / development | NESTED / false | NESTED / false |
| JOIN / DISCONNECT / количество bindings | 4 / 4 / 1 | 4 / 4 / 1 |
| Реальные fixture callbacks GAME / CHAT | 12 / 1 | 12 / 1 |
| Payload / игнорируемые fixture messages | 8 / 5 | 8 / 5 |
| Root, sibling, nested sibling hover | PASS | PASS |
| Fallback server ID, смена sidebar, negative host | PASS | PASS |
| Сброс состояния и повторный вход | PASS | PASS |
| Клиентские тики за прогон | 699 | 707 |
| Нормальный exit client / server | 0 / 0 | 0 / 0 |
| Независимые runtime + slice packaging assertions | 22 / 22 | 22 / 22 |

Непосредственные результаты и сырые логи:

- [1.21.4 result](results/phase2/1.21.4/result.json), [client](results/phase2/1.21.4/client.txt), [server](results/phase2/1.21.4/server.txt).
- [26.3 result](results/phase2/26.3/result.json), [client](results/phase2/26.3/client.txt), [server](results/phase2/26.3/server.txt).
- [Сводная проверка](results/phase2-summary.json): **44/44** assertions, по 22 на цель.
- [Проверка упаковки и публикации](results/phase2-artifact-checks.json): **44 PASS, 0 FAIL**.
- [Паритет source](results/phase2-source-parity.json): **14 PASS**.
- [Сборка и публикация](results/phase2/phase2-build-03.txt): `BUILD SUCCESSFUL`, core harness **4 sessions / 8 payload**.
- [SHA-256 двух опубликованных SDK JAR](results/phase2/published-artifacts.json).
- [Диагностика DISCONNECT](results/phase2/disconnect-diagnostics.txt).

`sdk_messages=17` на каждой цели: 13 fixture messages плюс четыре `PHASE2_END`.
Маркеры завершения проходят настоящий GAME callback, но не являются банами и не
включаются в 13 проверяемых содержательных сообщений. Количество тиков зависит от
загрузки клиента; оно не используется как фиксированное ожидаемое значение.

В каждом SDK — одна копия core и один выбранный HoverAccess. Другие трекеры,
fixtures и внешний Gson/Fabric API внутрь SDK не попадают. Core не указан как
транзитивная Maven dependency и не расплющен отдельно в consumer. `fabric.mod.json`
имеет точную Minecraft-версию, Java constraint и зависимости Loader/Fabric API;
consumer явно требует ту же версию SDK. Источником production runtime являются
готовые упакованные JAR, не classes из IDE/runClient.

Сравнение `git diff --exit-code` для `fte-api`, корневых build/settings/properties,
Gradle wrapper, JitPack, README, DOC и AGENTS завершилось с кодом **0**. Изменения
остались внутри `poc/`; release Maven-публикации не запускались.

Вывод: сборочная архитектура выдержала реальный BanTracker/ServerContext с одним
небольшим исключением Hover API на цель. Найденная ошибка передачи DISCONNECT
потребовала изменения платформенной границы, но не reflection, отдельных копий
трекера или изолированных Gradle builds. Это доказательство вертикального среза
на двух целях, не разрешение на массовый перенос или заявление о всех версиях.

## Попытки, ошибки и решения

| Попытка / проблема | Выбор и результат |
| --- | --- |
| Компиляция test driver 26.3: отсутствует `Minecraft.getOverlay()` | Проверка была лишней UI-зависимостью тест-драйвера. Автоподключение перенесено на 100-й клиентский тик; SDK adapter не использует этот UI API. Рефлексия не применялась. |
| Attempt 01, 1.21.4 | GAME/CHAT и первые payload прошли, reconnect остановился на off-thread DISCONNECT. Исправлена граница lifecycle, затем тест повторён. |
| Attempt 01, 26.3 | Vanilla server включил whitelist по умолчанию; вход был отклонён до игрового JOIN. Fixture явно задаёт `white-list=false` и `enforce-whitelist=false`, только на loopback. |
| Attempt 01, остановка пустого сервера | Vanilla pause спустя 60 секунд остановил tick callback, читавший stop-файл. Fixture теперь задаёт `pause-when-empty-seconds=0`. |
| Attempt 02, 1.21.4 | Клиент прошёл 4 сессии/13 callbacks/8 payload, но runner смотрел в другой каталог: поздний общий Gradle-блок переопределял runDir. Выбор runDir объединён в одно присваивание; успешный клиентский JSON и FAIL runner сохранены отдельно. |
| Attempt 02, 26.3 | Native exit 0xC0000409 до JOIN/fixture callbacks, после инициализации графической подсистемы. Java hs_err не найден. Первопричина не установлена; не маскируется как ошибка SDK и не объявляется исправленной. Лог сохранён, выполнен повтор. |
| Attempt 03 | Loom пометил `phase2Server` UP-TO-DATE: процесс не запускался. Runner правильно вернул FAIL. Для production client/server явно задано `outputs.upToDateWhen { false }`. |
| Attempt 04, 1.21.4 | Полный PASS с передачей DISCONNECT через `client.execute()`. |
| Attempt 04, 26.3 | После первой сессии потерялась отложенная обработка DISCONNECT; runner вернул timeout/FAIL. Сохранены raw callback, thread dump и нормальное продолжение render loop. Байткод подтвердил `dropAllTasks()` в disconnect; выбран платформенный handoff, переживающий очистку executor. |
| Финальный прогон, обе цели | Платформенная очередь DISCONNECT; полные четыре сессии на цель, точные payload, exit client/server=0. |
| Повторная Maven-публикация SDK после изменения lifecycle | Использованы отдельные версии 0.0.3-phase2 и 0.0.4-phase2; проверяется совпадение версии consumer и публикуемой версии, старые remapped SDK не переиспользуются. |

Сырые логи неудачных попыток и результаты runner сохранены в
`results/phase2/attempt-01`, `attempt-02`, `attempt-03`, `attempt-04`. Это история/диагностика,
они не включаются в итоговую успешную матрицу как пройденные проверки.

В логах также могут присутствовать предупреждения offline-mode, fake-token
Minecraft Services/Realms, Windows Perflib и LWJGL/Unsafe. Они сохраняются без
подавления и не используются вместо положительных утверждений теста.
Vanilla flat generator сообщает `No key layers in MapLike[{}]` при пустых
generator-settings; сервер использует существующий fixture world и принимает
подключения. Эта особенность тестового мира не относится к парсеру/SDK.

## Отдельно о потоках и сети

Здесь проверена принадлежность игровых операций клиентскому потоку, включая
переход Netty DISCONNECT → Render thread. Публикация единого immutable `State`
позволяет читать согласованные serverId/IP/onFuntime; несколько независимых
volatile полей не сделали бы изменение сессии атомарным. Callback регистрации
живут весь процесс, а активность трекера ограничена JOIN/DISCONNECT; повторный
вход не регистрирует дополнительные listeners.

Recording sink выполняется синхронно на клиентском потоке. Он подтверждает
payload, но не подтверждает неблокирующее поведение настоящего REST/WSS sender.
Отдельная статическая оценка release сети сохранена в [NETWORK_REVIEW.md](NETWORK_REVIEW.md).
Проблемы очередей, reconnect/cancellation и shutdown сетевых клиентов не
исправлялись и этим этапом не считаются проверенными. При последующем подключении
реального sender понадобятся отдельные проверки сессии/отмены и fake backend.

## Источники проверки

- [Fabric Events](https://docs.fabricmc.net/develop/events/): регистрация callbacks; проверялись настоящие события, доставленные игрой.
- [Loom Production Run Tasks](https://docs.fabricmc.net/develop/loom/production-run-tasks): production client/server tasks для запуска собранных JAR.
- Пинованные sources JAR Fabric networking **4.3.9+f371ccb904** и **6.3.10+5c1fee4d5d**, используемых соответствующими Fabric API. Ссылка на официальный Maven, SHA-256 modern sources и строки ConnectionMixin/AbstractNetworkAddon сохранены в диагностике.
- Байткод фактически используемого Minecraft **26.3**, `javap -c -p net.minecraft.client.Minecraft`, метод `disconnect(Screen, boolean, boolean)`. Минимальный фрагмент с `dropAllTasks()` сохранён в диагностике; thread dump относится к неудачной попытке 04.
- Неизменённые release BanTracker/ServerContext/BanPayload/HoverEventUtil; SHA исходников записаны проверкой паритета.

## Воспроизведение

Из `poc/multiversion`, при установленных JDK 21/25:

```powershell
rtk proxy .\gradlew.bat pocBuild pocPublishLocal :mc1.21.4:remapServerFixtureJar :mc26.3:serverFixtureJar --console=plain
rtk proxy python -X utf8 verify_source_parity.py
rtk proxy python -X utf8 run_phase2.py --target all
rtk proxy python -X utf8 verify_artifacts.py --version 0.0.4-phase2 --output results/phase2-artifact-checks.json
rtk proxy python -X utf8 verify_phase2.py
```

Каждый consumer имеет собственный Gradle root и получает SDK через Maven Local.
`prodClient` сам компилирует/упаковывает изменившийся consumer перед запуском.
Для раздельной диагностики доступны `run_phase2.py --target 1.21.4` и `--target 26.3`.
Порты 27414/27633 должны быть свободны. Серверы offline и привязаны к loopback;
runner проверяет нормальный exit обоих процессов и использует ограниченные timeout.

Логи финальных server/client и полный JSON результата сохраняются как обычные
текстовые файлы в `results/phase2/<version>/`, без исключения `.gitignore` для `.log`.
Время внутри логов — время хоста, не обещание локального времени пользователя.

## Пределы доказательства

- Реальные Fabric runtime и TCP проверены только на двух версиях, не на 13.
- Это локальный fixture server, не production FunTime и не online authentication.
- Реальный transport backend не вызывается: проверяется сформированный прежний payload.
- Не переносились остальные трекеры и весь публичный Builder SDK.
- Путь подписанного player chat не проверялся; GAME и profileless CHAT проверяются отдельно.
- Обход hover остаётся root/siblings, как в прежнем utility; поиск внутри аргументов
  translatable text не добавлялся. Проблему старого поведения не скрываем новой логикой.
- Это функциональный тест, не FPS/allocations benchmark и не исчерпывающий race stress test.
- Нативный сбой одной попытки 26.3 требует отдельного воспроизведения/диагностики
  окружения; успешный повтор сам по себе не устанавливает его причину.
