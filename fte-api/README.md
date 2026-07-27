# FunTimeEvents API (FTE SDK)

Клиентская Java-библиотека для Minecraft Fabric, реализующая автоматическое отслеживание событий игрового мира на серверах FunTime.

## Возможности

- Перехват **всех** сообщений чата
- Отслеживание банов (с парсингом hover-текста)
- Игроки в TAB-листе (donate, online status)
- Сканирование Города Вардена и Медного данжа (сундуки, игроки, экипировка)
- Отслеживание эвентов (Гейзер, Вулкан, Метеоритный дождь, Маяк Убийца)
- Боссбар «Адская резня» — mobs count
- Игроки вокруг шахты на спавне (mine lobby)
- Отправка капчи на решение
- GET-запросы к бекенду (события, шахты, игроки, баны, данжи)
- SSE-стримы с локальным кешем (события, шахты)
- Публичный API: `pollEvents()`, `onEvent()`, `fetchXxx()`, `getCachedXxx()`
- Каждый трекер можно отключить в билдере

## Требования

- Java 21
- Minecraft 1.21.4
- Fabric Loader 0.16+
- Fabric API

## Быстрый старт

### 1. Добавь зависимость

Опубликуй SDK в локальный Maven-репозиторий:

```bash
cd FunTimeEventsSDK
gradlew :fte-api:publishToMavenLocal -Pmod_version=1.0-SNAPSHOT
```

**`build.gradle` своего мода:**

```groovy
repositories {
    mavenLocal()
}

dependencies {
    modImplementation "com.funtimeevents:fte-api:1.0-SNAPSHOT"
}
```

### 2. Минимальная инициализация

В `ClientModInitializer.onInitializeClient()`:

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .build();
```

### 3. Онлайн-режим с API-ключом

```java
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .apiKey("your-api-key")
    .build();
```

### 4. Подписка на локальные события

```java
FunTimeEventsAPI.onEvent(event -> {
    if (event instanceof ChatMessageEvent msg) {
        System.out.println(msg.sender() + ": " + msg.text());
    } else if (event instanceof BanDetectedEvent ban) {
        System.out.println("Ban: " + ban.playerName());
    }
});

// Или pull:
List<FteEvent> events = FunTimeEventsAPI.pollEvents();
```

### 5. GET-запросы к бекенду

```java
// Получить список событий с фильтрами:
FunTimeEventsAPI.fetchEvents(Map.of("server_id", "102", "level", "Легендарный"))
    .thenAccept(json -> System.out.println(json));

// Получить шахты:
FunTimeEventsAPI.fetchMines(Map.of("server_id", "102"))
    .thenAccept(System.out::println);

// Получить игроков:
FunTimeEventsAPI.fetchPlayers(Map.of("player_name", "Rizzener"))
    .thenAccept(System.out::println);

// Получить баны:
FunTimeEventsAPI.fetchBans(Map.of("server_id", "102"))
    .thenAccept(System.out::println);

// Все медные данжи:
FunTimeEventsAPI.fetchCopperDungeon()
    .thenAccept(System.out::println);

// Все города вардена:
FunTimeEventsAPI.fetchWardenCity()
    .thenAccept(System.out::println);
```

### 6. Кеш из SSE-стримов

Автоматически наполняется при online-инициализации:

```java
// Актуальный кеш событий (обновляется стримом):
Map<String, JsonObject> events = FunTimeEventsAPI.getCachedEvents();

// Актуальный кеш шахт:
Map<String, JsonObject> mines = FunTimeEventsAPI.getCachedMines();
```

### 7. Отправка капчи

```java
// Отправить скриншот капчи на решение:
FunTimeEventsAPI.sendCaptcha(new CaptchaPayload("base64-encoded-screenshot"));
```

## Конфигурация Builder

| Метод | По умолчанию | Описание |
|-------|-------------|----------|
| `.userAgent(String)` | **обязательный** | `User-Agent` HTTP-заголовок |
| `.apiKey(String)` | — | Ключ для `X-API-Key` |
| `.baseUrl(String)` | `https://api.funtimeevents.su/v1` | URL бекенда |
| `.logLevel(LogLevel)` | `INFO` | `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG` |
| `.tickIntervalSeconds(int)` | `10` | Периодичность сканирования |
| `.offlineMode()` | — | Не создавать HTTP-клиент |
| `.disableScanTabPlayers()` | — | Не сканировать TAB и не слать `/players` |
| `.disableBansTracker()` | — | Не отслеживать баны и не слать `/bans` |
| `.disableScanDungeon()` | — | Не сканировать данжи и не слать `/copper-dungeon` `/warden-city` |
| `.disableScanHellMap()` | — | Не отслеживать Адскую резню и не слать `/events/hell-map` |
| `.disableScanMine()` | — | Не сканировать mine lobby и не слать `/mines/players-around` |
| `.disableEvent coordssTracker()` | — | Не отслеживать спавн-эвенты и не слать `/events/coordinates` |

### Примеры конфигурации

```java
// Только баны и эвенты в режиме DEBUG:
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .apiKey("secret")
    .logLevel(FteConfig.LogLevel.DEBUG)
    .disableScanTabPlayers()
    .disableScanDungeon()
    .disableScanHellMap()
    .disableScanMine()
    .build();

// Раз в 30 секунд:
FunTimeEventsAPI.builder()
    .userAgent("MyMod/1.0")
    .apiKey("secret")
    .tickIntervalSeconds(30)
    .build();
```

## Локальные события (EventBus)

| Событие | Поля |
|---------|------|
| `ChatMessageEvent` | `sender`, `text`, `timestamp` |
| `BanDetectedEvent` | `playerName`, `rawHoverText`, `timestamp` |

## Отправляемые данные (POST)

| Endpoint | Payload | Трекер |
|----------|---------|--------|
| `POST /players` | `TabPlayersPayload` | TabTracker |
| `POST /bans` | `BanPayload` | BanTracker |
| `POST /copper-dungeon` | `DungeonPayload` | DungeonTracker |
| `POST /warden-city` | `DungeonPayload` | DungeonTracker |
| `POST /events/hell-map` | `HellMapPayload` | HellMapTracker |
| `POST /mines/players-around` | `MinePlayersAroundPayload` | MineTracker |
| `POST /events/coordinates` | `EventCoordinatesPayload` | Event coordsTracker |
| `POST /captcha` | `CaptchaPayload` | — |

## Получаемые данные (GET)

| Endpoint | Метод SDK | Параметры |
|----------|----------|-----------|
| `GET /events` | `fetchEvents(params)` | `server_id`, `server_type`, `name`, `level` |
| `GET /mines` | `fetchMines(params)` | `server_id`, `server_type`, `rarity`, `time_before` |
| `GET /players` | `fetchPlayers(params)` | `player_name`, `search`, `server_id`, `server_type`, `donate`, `active_after`, `limit` |
| `GET /bans` | `fetchBans(params)` | `player_name`, `server_id`, `server_type`, `reason`, `limit`, `banned_after` |
| `GET /copper-dungeon` | `fetchCopperDungeon()` | — |
| `GET /warden-city` | `fetchWardenCity()` | — |

## Архитектура

```
fte-api/
  api/          — FunTimeEventsAPI (Builder), FteConfig
  spi/          — PayloadSender interface
  bootstrap/    — жизненный цикл (JOIN/DISCONNECT)
  tracker/      — Tracker + TrackerManager + все трекеры
  scheduler/    — tick-based периодический планировщик
  event/        — FteEvent, EventBus, ChatMessageEvent, BanDetectedEvent
  model/        — DTO для API-запросов
  net/          — ApiClient (HTTP, POST + GET + SSE)
  net/cache/    — EventCache, MineCache (SSE-стримы)
  util/         — FteLogger, ServerDetector
```

## Сборка SDK

```bash
cd FunTimeEventsSDK
gradlew :fte-api:build -Pmod_version=1.0-SNAPSHOT
```

## Публикация в Maven

```bash
gradlew :fte-api:publish -Pmod_version=1.0-SNAPSHOT \
    -Pmaven_publish_url=https://your-repo.example.com/releases \
    -Pmaven_publish_user=user \
    -Pmaven_publish_password=pass
```

## Лицензия

MIT
