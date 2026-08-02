# FunTimeEvents SDK — Полная документация

## О проекте

**FunTimeEvents SDK** — клиентская Java-библиотека для Minecraft Fabric, которая автоматически отслеживает игровые события на серверах FunTime и отправляет их на бэкенд.

**Зачем:** Мод собирает данные в реальном времени (баны, игроки в TAB, сундуки в данжах, информацию о ивентах, боссбар Адской резни, игроков вокруг авто-шахты на спавне) и отправляет их на централизованный бэкенд через WebSocket Relay или HTTP REST. Бэкенд агрегирует данные со всех подключённых клиентов, позволяя каждому участнику видеть актуальную информацию обо всех анархиях — баны, активные ивенты, шахты, состояние данжей и т.д.

### Возможности

- Отслеживание банов (парсинг hover-текста из чата)
- Сканирование TAB-листа (имена, донат-префиксы)
- Сканирование Медного данжа и Города Вардена (сундуки, экипировка игроков)
- Отслеживание ивентов по координатам
- Боссбар «Адская резня» — количество мобов
- Игроки вокруг авто-шахты на спавне (auto-mine)
- Решение капчи (REST, всегда)
- GET-запросы к бэкенду (события, шахты, игроки, баны, данжи)
- SSE-стримы с локальным кешем (REST-режим)
- WebSocket Relay со снапшотами каждый 1 сек (WSS-режим)
- Каждый трекер можно отключить в билдере
- Offline-режим (без сети) для тестов

---

## Требования

- **Java 21+**
- **Minecraft 1.21.x** (Проверено на 1.21.4, 1.21.8, 1.21.11)
- **Fabric Loader** 0.16+
- **Fabric API**

---

## Подключение (Build)

### Способ 1: JitPack (рекомендуется)

Без локальной сборки SDK — JitPack собирает автоматически по тегу:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    modImplementation "com.github.Rizzener.FunTimeEventsSDK:fte-api:v1.0.5"
}
```

### Способ 2: Локальная сборка (mavenLocal)

```bash
cd C:\dev\FunTimeEventsSDK
.\gradlew :fte-api:publishToMavenLocal -Pmod_version=1.0.5-SNAPSHOT
```

```groovy
repositories {
    mavenLocal()
}

dependencies {
    modImplementation "net.funtimeevents:fte-api:1.0.5-SNAPSHOT"
}
```

---

## Архитектура

```
fte-api/src/main/java/net/funtimeevents/
  api/               — FunTimeEventsAPI.java (Builder facade), FteConfig.java (конфиг)
  spi/               — PayloadSender.java (интерфейс для отправки данных)
  bootstrap/         — Bootstrap.java (жизненный цикл: JOIN/DISCONNECT)
  tracker/           — Tracker.java (интерфейс), TrackerManager.java, 8 реализаций
    ban/             — BanTracker.java
    dungeon/         — DungeonTracker.java
    eventcoordinates/— EventCoordinatesTracker.java
    hell/            — HellMapTracker.java
    mine/            — MineTracker.java
    tab/             — TabTracker.java
    server/          — ServerContext.java (синглтон, детектит сервер)
  scheduler/         — Scheduler.java (тиковый планировщик)
  model/             — 25 DTO классов (Payload + Response)
  net/               — ApiClient.java (REST), RelayClient.java (WSS)
    cache/           — RelayCache.java, SseCache.java (кеши)
  util/              — FteLogger.java, GsonHolder.java, PlayerNameUtil.java, ServerDetector.java,
                       TextUtil.java, HoverEventUtil.java, TextDisplayUtil.java, BossBarUtil.java
```

### Data Flow

```
Tracker (например BanTracker)
  → this.sender.sendBan(payload)     ← PayloadSender (интерфейс)
     → ApiClient.sendBan()           ← REST-режим (.disableWebSocket())
     → RelayClient.sendBan()         ← WSS-режим (по умолчанию)
        → JSON POST или WebSocket-фрейм
```

---

## Инициализация

### Минимальная (offline)

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .build();
// Трекеры работают, но ничего не отправляют
```

### Полная (онлайн, WSS — рекомендуется)

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")           // REQUIRED
    .apiKey("sk-fte-...")             // REQUIRED
    .build();                          // WSS по умолчанию
```

### REST-режим (без WebSocket)

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .apiKey("sk-fte-...")             // REQUIRED
    .disableWebSocket()               // Все POST через HTTP
    .build();
```

---

## Конфигурация Builder (`FteConfig.Builder`)

| Метод | По умолчанию | Описание |
|-------|-------------|----------|
| `.userAgent(String)` | **обязательный** | User-Agent для всех HTTP-запросов |
| `.apiKey(String)` | **обязательный** | Ключ для X-API-Key — свой на каждого клиента, ротировать при компрометации |
| `.baseUrl(String)` | `https://api.funtimeevents.su/v1/` | URL бэкенда |
| `.logLevel(LogLevel)` | `INFO` | `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG` |
| `.tickIntervalSeconds(int)` | `10` | Периодичность сканирования трекеров (мин. 1) |
| `.offlineMode()` | — | Без сети (трекеры работают, отправки нет) |
| `.disableWebSocket()` | — | REST вместо WSS |
| `.disableCompression()` | — | Отключить gzip для HTTP (WSS всегда без gzip) |
| `.disableScanTabPlayers()` | — | Отключить TAB-сканер |
| `.disableBansTracker()` | — | Отключить отслеживание банов |
| `.disableScanDungeon()` | — | Отключить сканирование данжей |
| `.disableScanHellMap()` | — | Отключить боссбар Адской резни |
| `.disableScanMine()` | — | Отключить auto-mine |
| `.disableEventCoordinatesTracker()` | — | Отключить координаты ивентов |

### Пример гибкой настройки

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .apiKey("sk-fte-...")             // REQUIRED
    .logLevel(FteConfig.LogLevel.DEBUG)
    .tickIntervalSeconds(5)
    .disableScanTabPlayers()
    .disableScanDungeon()
    .build();
```

---

## WSS vs REST — что и когда используется

| Аспект | WSS (по умолчанию) | REST (.disableWebSocket()) |
|--------|-------------------|---------------------------|
| POST (отправка данных) | RelayClient (WebSocket) | ApiClient (HTTP/1.1) |
| GET (запросы) | ApiClient (всегда REST) | ApiClient |
| Captcha | ApiClient (всегда REST) | ApiClient |
| Кеш данных | RelayCache (снапшоты каждую 1с) | SseCache (SSE-стримы) |
| SystemInfo | Доступно | null |

---

## Public API — статические методы `FunTimeEventsAPI`

### GET-запросы (всегда REST)

```java
// Сырые JSON-строки:
CompletableFuture<String> events = FunTimeEventsAPI.fetchEvents(Map.of("server_id", "101"));
CompletableFuture<String> mines  = FunTimeEventsAPI.fetchMines(Map.of("server_id", "101"));
CompletableFuture<String> copper = FunTimeEventsAPI.fetchCopperDungeon();
CompletableFuture<String> warden = FunTimeEventsAPI.fetchWardenCity();

// Типизированные ответы:
CompletableFuture<PlayersListResponse> players = FunTimeEventsAPI.fetchPlayers(Map.of("player_name", "Steve"));
CompletableFuture<BansListResponse> bans       = FunTimeEventsAPI.fetchBans(Map.of("server_id", "101", "limit", "10"));
```

### Кешированные данные (WSS relay / SSE)

```java
// Обновляются автоматически. Возвращают пустой список, если данных нет или они устарели >5 сек.
List<EventResponse>       events     = FunTimeEventsAPI.getEvents();     // системные ивенты
List<EventResponse>       userEvents = FunTimeEventsAPI.getUserEvents(); // user-ивенты (аирдропы, алтари, маяки)
List<MineResponse>        mines      = FunTimeEventsAPI.getMines();
List<LootAreaResponse>    copper     = FunTimeEventsAPI.getCopperDungeons();
List<LootAreaResponse>    warden     = FunTimeEventsAPI.getWardenCities();
SystemInfo                info       = FunTimeEventsAPI.getSystemInfo(); // null в REST-режиме
```

### Captcha (всегда REST, `POST /captcha`)

```java
// Решить капчу — вернёт текст и confidence
FunTimeEventsAPI.solveCaptcha("base64-encoded-png")
    .thenAccept(response -> {
        if (response != null && response.solved()) {
            System.out.println("Solved: " + response.text());
            System.out.println("Confidence: " + response.overallPercent() + "%");
        }
    });
```

---

## Модели (DTO)

### Payloads (POST-запросы, final поля + конструктор)

| Класс | Поля | Использование |
|-------|------|-------------|
| `TabPlayersPayload` | `serverId`, `serverType`, `playersList: List<ObservedPlayer>` | TabTracker |
| `BanPayload` | `serverId`, `serverType`, `playerName`, `reason`, `end` | BanTracker |
| `DungeonPayload` | `serverId`, `serverType`, `chests: List<ChestInfo>`, `players: List<PlayerGearInfo>` | DungeonTracker |
| `HellMapPayload` | `serverId`, `serverType`, `mobsCount` | HellMapTracker |
| `MinePlayersAroundPayload` | `serverId`, `serverType`, `playersAround: List<ObservedPlayer>` | MineTracker |
| `EventCoordinatesPayload` | `serverId`, `serverType`, `event`, `level`, `coordinates: EventCoordinates` | EventCoordinatesTracker |

### Вложенные payload-модели

| Класс | Поля | Назначение |
|-------|------|-----------|
| `ObservedPlayer` | `playerName`, `donate`, `seenAt` | Игрок в TAB / у авто-шахты |
| `ChestInfo` | `x`, `y`, `z`, `timeLeft` | Сундук в данже (от клиента) |
| `PlayerGearInfo` | `playerName`, `donate`, `helmet`, `chestplate`, `leggings`, `boots`, `isInvisible` | Экипировка игрока в данже |
| `EventCoordinates` | `x`, `y`, `z` (Integer, nullable) | Координаты ивента |

### Responses (GET/SSE/snapshot, mutable поля, нет конструктора)

| Класс | Поля | Назначение |
|-------|------|-----------|
| `EventResponse` | `serverId`, `serverType`, `name`, `status`, `timeLeft`, `level`, `levelUpdatedAt`, `coordinates: EventCoordinates`, `message`, `eventInfo: EventInfoResponse`, `updatedAt` | Активный ивент |
| `EventInfoResponse` | `mobsCount: Integer` | Детали ивента |
| `MineResponse` | `serverId`, `serverType`, `rarity`, `timeLeft`, `mineInfo: MineInfoResponse`, `updatedAt` | Активная шахта |
| `MineInfoResponse` | `playersAround: List<ObservedPlayer>`, `updatedAt` | Игроки вокруг шахты |
| `LootAreaResponse` | `serverId`, `serverType`, `chests: List<ChestResponse>`, `players: List<PlayerGearInfo>` | Данж (Copper / Warden) |
| `ChestResponse` | `x`, `y`, `z`, `timeLeft`, `createdAt` | Сундук из бэкенда |
| `BanResponse` | `serverId`, `serverType`, `playerName`, `reason`, `end`, `bannedAt` | Бан-рекорд |
| `BansListResponse` | `data: List<BanResponse>` | Список банов |
| `PlayersListResponse` | `data: List<PlayerDataResponse>` | Список игроков |
| `PlayerDataResponse` | `playerName`, `donate`, `active`, `serverId`, `serverType`, `serverHistory: List<ServerHistoryEntry>` | Метаданные игрока |
| `ServerHistoryEntry` | `serverId`, `serverType`, `firstSeen`, `lastSeen` | История посещений |
| `Snapshot` | `type`, `events: List<EventResponse>`, `mines: List<MineResponse>`, `copperDungeons: List<LootAreaResponse>`, `wardenCities: List<LootAreaResponse>`, `systemInfo: SystemInfo`, `ts: double` | Relay-снапшот |
| `SystemInfo` | `events`, `mines`, `copperDungeons`, `wardenCities`, `clientsConnected`, `trackedAnarchies` (int) | Статистика бэкенда |
| `CaptchaResponse` | `solved: boolean`, `text: String`, `overallPercent: Double`, `results: List<PercentResult>` | Результат капчи |
| `PercentResult` | `number: String`, `confidencePercent: double` | Результат одной цифры |

---

## Endpoints

### POST (от трекеров)

| Endpoint | Payload | Трекер |
|----------|---------|--------|
| `POST /players` | `TabPlayersPayload` | TabTracker |
| `POST /bans` | `BanPayload` | BanTracker |
| `POST /copper-dungeon` | `DungeonPayload` | DungeonTracker |
| `POST /warden-city` | `DungeonPayload` | DungeonTracker |
| `POST /events/hell-map` | `HellMapPayload` | HellMapTracker |
| `POST /mines/players-around` | `MinePlayersAroundPayload` | MineTracker |
| `POST /events/coordinates` | `EventCoordinatesPayload` | EventCoordinatesTracker |

### GET (через SDK)

| Endpoint | Метод SDK | Параметры |
|----------|-----------|-----------|
| `GET /events` | `fetchEvents(params)` | `server_id`, `server_type`, `name`, `level` |
| `GET /mines` | `fetchMines(params)` | `server_id`, `server_type`, `rarity`, `time_before` |
| `GET /players` | `fetchPlayers(params)` | `player_name`, `search`, `server_id`, `server_type`, `donate`, `active_after`, `limit` |
| `GET /bans` | `fetchBans(params)` | `player_name`, `server_id`, `server_type`, `reason`, `limit`, `banned_after` |
| `GET /copper-dungeon` | `fetchCopperDungeon()` | — |
| `GET /warden-city` | `fetchWardenCity()` | — |

### SSE-стримы (только в REST-режиме)

| Endpoint | Кеш-класс | Ключ |
|----------|-----------|------|
| `GET /events/stream` | `SseCache<EventResponse>` | `name` |
| `GET /mines/stream` | `SseCache<MineResponse>` | `serverId_rarity` |
| `GET /copper-dungeon/stream` | `SseCache<LootAreaResponse>` | `serverId` |
| `GET /warden-city/stream` | `SseCache<LootAreaResponse>` | `serverId` |

---

## Трекеры — что и как отслеживают

### 1. BanTracker (`tracker/ban/BanTracker.java`)

Парсит сообщения чата на предмет бана — извлекает имя игрока, причину, длительность и сервер через hover-текст (`HoverEventUtil.extractHoverText()`).

### 2. TabTracker (`tracker/tab/TabTracker.java`)

Сканирует `playerList` из `ClientPlayNetworkHandler`, собирает имена и донат-префиксы через `PlayerNameUtil`.

**Не реализует `PayloadSender`** — данные собирает `TabTracker`, а отправляет их `TrackerManager.sendTabPlayersPayload()`.

### 3. DungeonTracker (`tracker/dungeon/DungeonTracker.java`)

Сканирует Медный данж и Город Вардена. Оптимизация: сканирует только когда игрок ВНУТРИ зоны данжа (проверяет позицию). Собирает:
- Сундуки (координаты + время до закрытия) — через `TextDisplayUtil.getDisplayText()`
- Игроков (имя, донат, экипировка, инвиз)

**Оптимизация:** donate-кеш в `scanPlayers()` (synchronized LinkedHashMap, 30s TTL) — `PlayerNameUtil.extractDonate()` вызывается только при промахе кеша.

### 4. HellMapTracker (`tracker/hell/HellMapTracker.java`)

Читает боссбар через `BossBarUtil.getBossBars()` и `BossBarUtil.getBossBarName()` — рефлексия `bossBars` поля + `getName()` в отдельной утилите.

### 5. MineTracker (`tracker/mine/MineTracker.java`)

На каждом тике ищет игроков вокруг авто-шахты на спавне (по координатам). Собирает имена и донат-префиксы.

### 6. EventCoordinatesTracker (`tracker/eventcoordinates/EventCoordinatesTracker.java`)

Парсит игровые сообщения чата через регулярные выражения:
- `||| [название_ивента] |||` — название ивента
- `Уровень лута: ...` — уровень
- `Появился на координатах [x y z]` — координаты

Отправка только при наличии координат в сообщении. Анонсы без координат (например, `Появился на Арене Смерти (/darena)`), а также отсутствие уровня, имени ивента или server_id — пропускаются (debug-лог).

### 7. ServerContext (`tracker/server/ServerContext.java`)

**Синглтон.** Определяет текущий сервер:
- IP сервера — FunTime? (`*.funtime.su`, `.sh`, `.me`, `.store`, `.network`, `.wiki`)
- ID сервера — из сайдбара (`Анархия-101` → `101`)

Все трекеры вызывают `ServerContext.getInstance()` для проверки `isOnFuntime()`, `getServerId()`, `getServerType()`.

---

## Logging

### Модульные теги

```java
FteLogger.info(FteLogger.RELAY, "connected");    // [FTE:RELAY] connected
FteLogger.warn(FteLogger.API, "HTTP 400");        // [FTE:API] HTTP 400
FteLogger.debug(FteLogger.TRACK, "5 players");    // [FTE:TRACK] 5 players
```

| Тег | Модуль |
|-----|--------|
| `CORE` | Жизненный цикл SDK, bootstrap, scheduler |
| `RELAY` | WebSocket connect/auth/watchdog/disconnect/flush |
| `API` | HTTP запросы/ответы/gzip |
| `CACHE` | SSE-стримы, парсинг снапшотов |
| `TRACK` | Все 7 трекеров (start/stop/data/ошибки) |

---

## Компрессия (gzip)

- Gzip применяется ТОЛЬКО к HTTP (ApiClient): заголовок `Content-Encoding: gzip` + `GZIPOutputStream`.
- WebSocket (RelayClient) НЕ использует gzip.
- `.disableCompression()` отключает gzip для HTTP.

---

## Offline-режим

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .offlineMode()
    .build();
```

- HTTP-клиент не создаётся
- GET-запросы возвращают `null`
- POST не отправляется
- Трекеры работают и собирают данные локально

---

## Cross-version compatibility (1.21.x)

SDK использует **утилиты рефлексии** для API Minecraft, которые отличаются в разных версиях Yarn-маппингов (все 5 точек сведены в `util/`):

1. **`HoverEventUtil`** — `getAction()` / `getValue()` / `value()` fallback
2. **`TextDisplayUtil`** — определение TextDisplay через `Class.isInstance` + кеш
3. **`BossBarUtil`** — `BossBarHud.bossBars` поле + `getName()`
4. **`PlayerNameUtil`** — `GameProfile.getName()` / `name()` fallback
5. **`PlayerNameUtil`** — `GameProfile.getId()` / `id()` fallback

Стабильные импорты (безопасны во всех версиях):
- `MinecraftClient`, `PlayerEntity`, `Blocks`, `Text`, `EquipmentSlot`
- `ClientReceiveMessageEvents`, `ClientTickEvents`, `ClientPlayConnectionEvents` (Fabric API)
- `Registries`, `ItemStack`, `BlockPos`, `Entity`

---

## Утилиты

### GsonHolder

```java
GsonHolder.INSTANCE  // Единый Gson для всего SDK
```

### TextUtil

```java
TextUtil.tryGetRawText(Text text);
// Быстрый доступ к plain-text без Text.visit() — null для не-plain контента
```

### HoverEventUtil

```java
HoverEventUtil.extractHoverText(Text message);
// Извлекает hover-текст из сообщения (рекурсивный поиск по siblings)
```

### TextDisplayUtil

```java
TextDisplayUtil.isTextDisplay(Entity entity);
TextDisplayUtil.getDisplayText(Entity entity);
// Определяет TextDisplay entity и получает текст через рефлексию getText()
// Fast-path: Class.isInstance() после первого разрешения класса
```

### BossBarUtil

```java
BossBarUtil.getBossBars();     // Map<?, ?> или null
BossBarUtil.getBossBarName(Object bar);  // Text или null
```

### PlayerNameUtil

```java
PlayerNameUtil.extractDonate(PlayerEntity player);
PlayerNameUtil.extractDonate(PlayerListEntry entry);
// Возвращает донат-префикс (всё до имени в display name)
// Имеет статический DONATE_CACHE с TTL 30с (ConcurrentHashMap)

PlayerNameUtil.getProfileName(Object profile);   // getName() / name() fallback
PlayerNameUtil.getProfileId(Object profile);     // getId() / id() fallback
```

### ServerDetector

```java
ServerDetector.isFuntime(serverIp);    // *.funtime.su / .sh / .me / .store / .network / .wiki
ServerDetector.extractServerId(text);   // "Анархия-101" → 101
ServerDetector.getCurrentServerIp();    // Текущий IP сервера
ServerDetector.getSidebarTitle();       // Текст сайдбара
```

---

## Известные особенности

1. **Кодировка:** Windows-консоль должна быть в UTF-8: `chcp 65001`
2. **Loom-кеш:** После любого изменения SDK — удалять `.gradle\loom-cache\remapped_mods\remapped\net\funtimeevents\` в проекте
3. **Gradle daemon:** При проблемах — `.\gradlew --stop` перед очисткой кеша
4. **fabric.mod.json:** НЕ объявляет `fabric-loader` и `fabric-api` — они предоставляются consumer-модом
5. **Версия SDK:** `FunTimeEventsAPI.getVersion()` — читает из `fabric.mod.json` в рантайме