# FunTimeEventsSDK — руководство разработчика

## Состояние выпуска

Кандидат `2.0.0-rc.1`, не опубликован. Проверка новых артефактов — PHASE6_RESULTS.md.
Исторический локальный PASS этапа 5 не подменяет новые hosted результаты.

## Версии и зависимости

| Minecraft | Java | Fabric API | Maven artifact |
|---|---:|---|---|
| 1.21.4 | 21 | `0.113.0+1.21.4` | `mc1.21.4` |
| 1.21.5 | 21 | `0.128.2+1.21.5` | `mc1.21.5` |
| 1.21.6 | 21 | `0.128.2+1.21.6` | `mc1.21.6` |
| 1.21.7 | 21 | `0.129.0+1.21.7` | `mc1.21.7` |
| 1.21.8 | 21 | `0.136.1+1.21.8` | `mc1.21.8` |
| 1.21.9 | 21 | `0.134.1+1.21.9` | `mc1.21.9` |
| 1.21.10 | 21 | `0.138.4+1.21.10` | `mc1.21.10` |
| 1.21.11 | 21 | `0.141.6+1.21.11` | `mc1.21.11` |
| 26.1 | 25 | `0.145.1+26.1` | `mc26.1` |
| 26.1.1 | 25 | `0.145.4+26.1.1` | `mc26.1.1` |
| 26.1.2 | 25 | `0.155.3+26.1.2` | `mc26.1.2` |
| 26.2 | 25 | `0.161.0+26.2` | `mc26.2` |
| 26.3 | 25 | `0.163.0+26.3` | `mc26.3` |


Gradle 9.6.1, Loom 1.17.21, Fabric Loader 0.19.5.
Для разработки нужны оба JDK (21/25); запускайте wrapper на JDK 25. Для запуска
consumer нужен JDK из строки Minecraft. Mappings старых версий — официальные Mojang;
release namespace старых JAR — intermediary после remapJar. Современные JAR используют
необфусцированные имена и не проходят remap. Не remap-те modern JAR вручную.

### Dependency для каждого target

Все строки используют один group и одну версию релиза. Выберите ровно одну:

- Minecraft 1.21.4: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.4:2.0.0-rc.1"`
- Minecraft 1.21.5: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.5:2.0.0-rc.1"`
- Minecraft 1.21.6: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.6:2.0.0-rc.1"`
- Minecraft 1.21.7: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.7:2.0.0-rc.1"`
- Minecraft 1.21.8: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.8:2.0.0-rc.1"`
- Minecraft 1.21.9: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.9:2.0.0-rc.1"`
- Minecraft 1.21.10: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.10:2.0.0-rc.1"`
- Minecraft 1.21.11: `modImplementation "com.github.Rizzener.FunTimeEventsSDK:mc1.21.11:2.0.0-rc.1"`
- Minecraft 26.1: `implementation "com.github.Rizzener.FunTimeEventsSDK:mc26.1:2.0.0-rc.1"`
- Minecraft 26.1.1: `implementation "com.github.Rizzener.FunTimeEventsSDK:mc26.1.1:2.0.0-rc.1"`
- Minecraft 26.1.2: `implementation "com.github.Rizzener.FunTimeEventsSDK:mc26.1.2:2.0.0-rc.1"`
- Minecraft 26.2: `implementation "com.github.Rizzener.FunTimeEventsSDK:mc26.2:2.0.0-rc.1"`
- Minecraft 26.3: `implementation "com.github.Rizzener.FunTimeEventsSDK:mc26.3:2.0.0-rc.1"`


Для распространения добавьте `include` с той же координатой. Для предоставляемого
отдельно SDK-мода include не нужен, но тогда именно пользователь обязан установить
подходящий JAR. Не распаковывайте SDK классы в consumer JAR, не relocate-те их и не
вкладывайте `core` отдельно. Fabric mod ID SDK — `fte-api`; metadata содержит точный
Minecraft target, Java constraint, Loader и Fabric API dependencies.

### Независимый минимальный consumer

В examples/consumer нет producer project dependencies или Maven Local. Для RC:

```sh
./gradlew sdkStage -Ptarget_versions=1.21.4
./gradlew -p examples/consumer build -Pminecraft_version=1.21.4 -Psdk_repository=/absolute/path/to/build/staging-maven
```

Для 26.3 поменяйте только minecraft_version и используйте соответствующий staging
target. Зависимость по умолчанию указывает на JitPack, применяйте её после публикации.
Точный ExampleClient.java приведён в README; ключ приходит из consumer-конфигурации.

## Builder и режимы

`userAgent(...)` обязателен. `apiKey(...)` обязателен в online режиме.
`baseUrl(...)` по умолчанию https://api.funtimeevents.su/v1/.
`logLevel(FteConfig.LogLevel.DEBUG)` меняет уровень модульного логирования.
Полная конфигурация сравнивается при повторном Builder: URL, ключ, userAgent,
режим, compression, флаги трекеров, интервал, logLevel. Несовпадение вызывает
IllegalStateException без раскрытия ключа. Чужой API-ключ незаметно не используется.

| Режим | Отправка трекеров | Получение/кеши | Captcha |
|---|---|---|---|
| По умолчанию | WSS relay, plain TEXT JSON | REST GET + WebSocket snapshots | REST |
| disableWebSocket() | REST POST | REST GET + SSE | REST |
| offlineMode() | без отправки | пустые caches, completed-null futures | без запроса |

`disableCompression()` отключает gzip HTTP POST. WSS всегда использует текстовый
JSON. Auth envelope, endpoint paths, JSON и прежние типы DTO не менялись при переносе.
Offline не является локальным хранилищем данных: payload отбрасывает no-op sender.

### Трекеры и интервалы

Все включены по умолчанию. Отключение: disableScanTabPlayers(), disableBansTracker(),
disableScanDungeon(), disableScanHellMap(), disableScanMine(),
disableEventCoordinatesTracker(). Dungeon включает Copper Dungeon и Warden City.
`tickIntervalSeconds(...)`: общий период 10 секунд по умолчанию, минимум 1 секунда.
Сохраняются прежние дополнительные интервалы HellMap и алгоритмы каждого трекера.
SDK реагирует на JOIN/DISCONNECT и сообщения; consumer не регистрирует свои callbacks.

### Публичные методы

Typed caches: getEvents(), getUserEvents(), getMines(), getCopperDungeons(),
getWardenCities(); getSystemInfo() доступен в WSS режиме.
Async GET: fetchEvents(params), fetchUserEvents(params), fetchMines(params),
fetchPlayers(params), fetchBans(params), fetchCopperDungeon(), fetchWardenCity().
Captcha: solveCaptcha(base64), всегда REST, даже при WSS.
Не блокируйте клиентский поток вызовом join/get на сетевых futures. Результат
обработайте асинхронно; изменение Minecraft UI верните на клиентский поток.

## Lifetime и shutdown

Игровая сессия и SDK runtime имеют разные сроки жизни. DISCONNECT останавливает
трекеры, очищает игровой context/donate caches и делает queued payload старой
сессии недействительными. Backend caches принадлежат runtime. Reconnect не создаёт
новые Fabric подписки. Не вызывайте shutdown при DISCONNECT.

Legacy shutdown() останавливает глобальный runtime. В текущем кандидате один
consumer может остановить SDK другого: это явный продуктовый риск, решение
lease/owner изложено в RELEASE_DECISIONS.md. До принятия решения централизуйте
владение runtime в одном host-моде. CLIENT_STOPPING очищает runtime автоматически.
Build/shutdown выполняются на клиентском потоке. Инициализация с ошибкой очищает
созданные ресурсы, сохраняя ранее установленный чужой runtime.

## Гарантии доставки

Snapshots: TAB, игроки шахт, состояние данжей, HellMap. Очередь до 64 ключей
type/server, latest wins, TTL 15 секунд; старое состояние заменяется новым.
Важные события: bans и coordinates, FIFO до 256, TTL 30 секунд, overflow отклоняет
новое событие. При shutdown очередь очищается; смена сессии инвалидирует payload.
Перед отправкой JSON/gzip выполняются вне client thread. Gzip failure отменяет
HTTP запрос: plain bytes не отправляются с Content-Encoding: gzip.

Это best effort. Нет durable spool, backend ACK, гарантии at-least-once или
exactly-once. HTTP/WS неоднозначный исход не повторяется слепо. При reconnect бан
может потеряться; приложению нельзя считать очередь журналом банов. Усиление
доставки требует отдельного решения по ACK/idempotency/retention backend.

## Несколько модов и Loader

Одинаковый `fte-api` одной версии в двух jar-in-jar должен разрешаться в один мод.
Используйте совместимые явные version constraints. Exact-конфликт SDK версий
должен завершаться диагностикой Loader. При диапазонах Loader может выбрать одну
совместимую версию; это не означает, что два runtime будут изолированы. Разные MC
targets никогда не должны загружаться в один клиент. Детали фактических негативных
проверок и бинарной совместимости находятся в PHASE6_RESULTS.md.

## Миграция с прежнего SDK

Старые tags не перепубликуются. Проверка v1.0.5 обнаружила HTTP 404 для
`com.github.Rizzener.FunTimeEventsSDK:fte-api:v1.0.5`, при этом доступен POM
`com.github.Rizzener:FunTimeEventsSDK:v1.0.5`. Подробности и raw ответы —
RELEASE_DECISIONS.md. Для нового релиза используйте multi-module group и artifactId
`mc<точная-версия>`, одновременно обновите include и metadata зависимости на
`fte-api`. Не используйте Maven relocation с fte-api на произвольный MC target:
Maven не выбирает target из вашей версии Minecraft. Не используйте агрегат repo,
который притянет несколько несовместимых targets.

Предлагается новый tag без префикса v, чтобы Fabric semantic version совпадала с
Maven version. Builder, пакеты facade/config/models и сетевые публичные дескрипторы проверяются
по старому JAR. Внутренние tracker/bootstrap constructors не являются обещанием
бинарной совместимости. Precompiled legacy probe запускается на новом SDK;
это не подтверждает совместимость всех неизвестных consumer-модов.

## Разработка и публикация

AGENTS.md содержит команды. Root Gradle — единственная producer сборка. Python
контролирует production процессы и доказательства; обычная сборка/IDE используют
Gradle напрямую. Public channel остаётся JitPack. RC не опубликован. Изолированное
Maven staging и GitHub artifact uploads не являются Maven release deployment.
Порядок публикации и сравнение каналов — RELEASE_DECISIONS.md.

Лицензия — MIT License with Backend Restriction; сохранить точный LICENSE.
Известная native проблема Windows 26.3 остаётся OPEN независимо от Linux PASS.
