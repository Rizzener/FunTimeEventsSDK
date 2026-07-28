# FunTimeEventsSDK — Agent Guide

## Build system

### Two-step build (MANDATORY)

Composite builds (`include project(":fte-api")`) DO NOT WORK with Fabric Loom.
The ONLY reliable workflow is standalone SDK build + mavenLocal consumption.

```bash
# Step 1: Build SDK and publish to local Maven
cd C:\dev\FunTimeEventsSDK
.\gradlew :fte-api:publishToMavenLocal -Pmod_version=1.0-SNAPSHOT

# Step 2: Clear consumer's Loom cache and build
cd C:\dev\IDEA\untitled
Remove-Item -Recurse -Force .gradle -ErrorAction SilentlyContinue
.\gradlew :build
```

**Consumer `build.gradle` (Groovy DSL):**
```groovy
repositories { mavenLocal() }
dependencies {
    modImplementation "net.funtimeevents:fte-api:1.0-SNAPSHOT"
}
```

**Consumer `settings.gradle`:** NO `include(":fte-api")` or `projectDir` lines.

### Gradle versions

- **Wrapper:** Gradle 9.6.1 (`gradle-wrapper.properties`)
- **Fabric Loom:** 1.17-SNAPSHOT (set in `settings.gradle.kts` pluginManagement, not in subproject)
- **Java:** 21 (sourceCompatibility + targetCompatibility)

The subproject `fte-api/build.gradle.kts` does NOT specify a fabric-loom version — it inherits from `settings.gradle.kts` pluginManagement.

### Properties in build.gradle.kts

All properties use `findProperty` with fallback defaults because consumers override them:
```kotlin
val minecraftVersion = (findProperty("minecraft_version") as String?) ?: "1.21.4"
```

The `fabric_api_version` property also falls back to `findProperty("fabric_version")` for compatibility with different consumer naming conventions.

### Loom cache gotcha

After ANY SDK code change, the consumer's Loom cache MUST be deleted:
```
C:\dev\IDEA\untitled\.gradle\loom-cache\remapped_mods\remapped\net\funtimeevents\fte-api-*
```

If forgotten, the consumer will compile and run with OLD SDK code. No warning, no error — just silently stale.

Additionally, the Gradle daemon can hold stale Loom cache locks. When in doubt:
```bash
.\gradlew --stop
Remove-Item -Recurse -Force .gradle -ErrorAction SilentlyContinue
```

### Standalone build

For SDK-only compilation checks (no consumer needed):
```bash
cd C:\dev\FunTimeEventsSDK
.\gradlew :fte-api:build -Pmod_version=1.0-SNAPSHOT
```

---

## Architecture

### Package map (41 Java files)

```
api/ (2)            FunTimeEventsAPI.java (Builder facade), FteConfig.java (immutable config)
spi/ (1)            PayloadSender.java — KEY INTERFACE, breaks api↔tracker cycle
bootstrap/ (1)      Bootstrap.java (lifecycle: JOIN/DISCONNECT, ClientTickEvents)
tracker/ (9)        Tracker.java (interface), TrackerManager.java,
                    8 tracker impls: BanTracker, DungeonTracker, EventCoordinatesTracker,
                    HellMapTracker, MineTracker, TabTracker, TabHeaderTracker
  flat packages:    ban/, dungeon/, eventcoordinates/, hell/, mine/, tab/, tabheader/
scheduler/ (1)      Scheduler.java (tick-based, called from ClientTickEvents.END_CLIENT_TICK)
model/ (25)         All request payloads + response DTOs (flat package, no subpackages)
net/ (2+2)          ApiClient.java (REST POST+GET), RelayClient.java (WSS),
                    cache/RelayCache.java, cache/SseCache.java (generic SSE cache)
util/ (4)           FteLogger.java, GsonHolder.java, PlayerNameUtil.java, ServerDetector.java
```

### Data flow

```
Tracker (e.g. BanTracker)
  → this.sender.sendBan(payload)     ← PayloadSender injected via constructor
     → ApiClient.sendBan()           ← REST mode (.disableWebSocket())
     → RelayClient.sendBan()         ← WSS mode (default)
        → JSON POST or WebSocket frame
```

NO EventBus. Trackers send directly through PayloadSender. There is no local event queue or subscription system. All data goes to the backend via HTTP or WebSocket.

### Key interfaces

**`PayloadSender`** (`spi/PayloadSender.java`) — the seam that breaks the cyclic dependency. Defines 7 methods:
```java
sendTabPlayers(TabPlayersPayload)
sendBan(BanPayload)
sendCopperDungeon(DungeonPayload)
sendWardenCity(DungeonPayload)
sendHellMap(HellMapPayload)
sendMinePlayers(MinePlayersAroundPayload)
sendEventCoordinates(EventCoordinatesPayload)
```

Two implementations:
- `ApiClient` (REST) — HTTP/1.1 POST requests
- `RelayClient` (WSS) — WebSocket `{"type":"...","body":{...}}` frames

**Never add `sendCaptcha` to PayloadSender.** Captcha always uses REST, never WebSocket.

### Tracker lifecycle

- `start()` — called on world join (ClientPlayConnectionEvents.JOIN)
- `stop()` — called on world leave (ClientPlayConnectionEvents.DISCONNECT)
- `tick()` — called every ~10s (configurable) from Scheduler via ClientTickEvents
- Constructor receives `PayloadSender` — never use static methods from FunTimeEventsAPI

Trackers are registered in `TrackerManager` constructor conditionally based on `FteConfig` flags:
```java
if (config.bansEnabled()) trackers.add(new BanTracker(sender));
if (config.dungeonEnabled()) trackers.add(new DungeonTracker(sender));
// ...
```

### FunTimeEventsAPI — Builder pattern

```java
FunTimeEventsAPI.builder()
    .userAgent("ModName/1.0")       // REQUIRED
    .apiKey("sk-fte-...")           // REQUIRED in online mode
    .baseUrl("https://host/v1/")    // default: api.funtimeevents.su/v1/
    .logLevel(LogLevel.DEBUG)       // default: INFO
    .disableScanTabPlayers()        // disable individual trackers
    .disableWebSocket()             // opt-out of WSS, use REST-only
    .disableCompression()           // opt-out of HTTP gzip, send plain JSON
    .tickIntervalSeconds(5)        // default: 10
    .build();
```

Defaults:
- `wsMode = true` (WSS relay for POST)
- `compression = true` (gzip HTTP POST bodies, WSS always plain text)
- `baseUrl = "https://api.funtimeevents.su/v1/"`
- `tickIntervalSeconds = 10` (stored as 200 ticks)
- All trackers enabled

Validation: `userAgent` is required. `apiKey` is required when not offline. `baseUrl` defaults if not set.

### Compression (.disableCompression())

Gzip is applied ONLY to HTTP (ApiClient): `Content-Encoding: gzip` header + `GZIPOutputStream` body.
WebSocket (RelayClient) does NOT use gzip — `sendBinary()` breaks Python backends (`KeyError: 'text'`).
`.disableCompression()` disables gzip for HTTP, WSS is unaffected either way.

### WSS vs REST modes

| Aspect | WSS (default) | REST (.disableWebSocket()) |
|--------|--------------|---------------------------|
| POST data | RelayClient (WebSocket) | ApiClient (HTTP/1.1) |
| GET data | ApiClient (always REST) | ApiClient |
| Captcha | ApiClient (always REST) | ApiClient |
| Cached data | RelayCache (snapshots) | SseCache (SSE streams) |
| Base URL transform | `https://`→`wss://` + `/relay` | — |

Both modes provide identical typed `get*()` methods: `getEvents()`, `getMines()`, `getCopperDungeons()`, `getWardenCities()`. `getSystemInfo()` only works in WSS mode.

### RelayClient connection lifecycle

1. `wss://host/v1/relay` — WebSocket handshake
2. Auth message: `{"type":"auth","api_key":"..."}`
3. Server responds: `{"type":"auth_ok"}` — counts down authLatch (30s timeout)
4. Server sends snapshot every 1s: `{"type":"snapshot","events":[...],...}`
5. Connection stays open until `onClose`/`onError` → closeLatch.countDown() → reconnect loop

Two-latch pattern:
- `authLatch` — 30s timeout, waiting for auth_ok. If timeout → reconnect
- `closeLatch` — no timeout, waiting for connection drop. Blocks until disconnect

**Watchdog (silent disconnect protection):** `FTE-Relay-Watchdog` daemon thread polls every 1s via `closeLatch.await(1, SECONDS)`. If `System.currentTimeMillis() - lastSnapshotAt > 15000` (no snapshots for 15s), calls `ws.abort()` + manually cleans up `webSocket`/`authenticated` + `closeLatch.countDown()` to force reconnect. This handles NAT timeouts and firewall drops where `onClose`/`onError` are never called.

**HttpClient reuse:** `HttpClient` is created once in constructor, reused across all reconnect attempts. Never use `HttpClient.newHttpClient()` inside `doConnect()`.

**IO thread (off-Render):** `FTE-Relay-IO` single-thread executor. `sendMessage()` offloads `GSON.toJson()` serialization to this thread to avoid blocking the Render Thread. `disconnect()` calls `ioExecutor.shutdownNow()`.

**JSON parsing:** `processMessage()` parses messages via `GSON.fromJson(msg, Map.class)` and checks the `type` field — NOT via `String.contains()`. This is tolerant of whitespace in JSON formatting.

---

## Thread safety

**ALL shared mutable fields MUST be `volatile`.**

Critical locations:
- `ChatTracker.active`, `BanTracker.active`, `HellMapTracker.active`, `EventCoordinatesTracker.active` — written on ClientTick thread, read on Netty event callbacks
- `Bootstrap.running`, `Bootstrap.started` — written in join/leave events
- `TabHeaderTracker.serverId`, `serverIp`, `onFuntime` — read from 6+ trackers across threads
- `RelayClient.webSocket` — written in WebSocket callback thread, read in render thread
- `RelayClient.lastSnapshotAt` — written in WebSocket listener thread, read in watchdog thread
- `FunTimeEventsAPI.instance` — synchronized double-checked locking in `create()`
- `Scheduler.running`, `Scheduler.task`

When adding any shared mutable field to a tracker or lifecycle class, add `volatile`.

---

## Naming conventions

### Tracker disable methods (FteConfig.Builder)
```
disableScanTabPlayers()
disableBansTracker()
disableScanDungeon()
disableScanHellMap()
disableScanMine()
disableEventCoordinatesTracker()
```

### Model classes
- **Payload** (POST request body): `*Payload.java` — have `final` fields + explicit constructor
- **Response** (GET/SSE/snapshot): `*Response.java` — mutable fields (Gson deserialization), no constructor
- Both use `@SerializedName` for snake_case mapping

### Endpoint paths
All paths in ApiClient are relative to baseUrl (which includes `/v1/`):
```java
postJson("/players", payload)         // NOT "/api/v1/players"
postJson("/events/coordinates", ...)
postJson("/mines/players-around", ...)
```

---

## Cross-version compatibility (1.21.x, 26.x)

The SDK uses **reflection** for Minecraft APIs that differ between Yarn mapping versions:

1. **`HoverEvent.Action.SHOW_TEXT`** (BanTracker) — `getAction()`, `getValue()` via reflection
2. **`TextDisplayEntity`** (DungeonTracker) — detected by class name `getSimpleName().contains("textdisplay")`, then `getMethod("getText")`
3. **`BossBarHud.bossBars`** (HellMapTracker) — `getDeclaredField("bossBars")` with `setAccessible(true)`, cached via `volatile` static field

**Do NOT use direct imports** of classes that may differ between Yarn versions. Use `var` for inferred types and reflection for method/field access.

Stable imports (safe across 1.21.x and 26.x):
- `MinecraftClient`, `PlayerEntity`, `Blocks`, `Text`, `EquipmentSlot`
- `ClientReceiveMessageEvents`, `ClientTickEvents`, `ClientPlayConnectionEvents` (Fabric API)
- `Registries`, `ItemStack`, `BlockPos`, `Entity`

---

## Logging

**Always use module tags.** FteLogger requires a module constant as first argument:

```java
FteLogger.info(FteLogger.RELAY, "connected");
FteLogger.info(FteLogger.TRACK, "Mine lobby: 5 players around spawn");
FteLogger.warn(FteLogger.API, "HTTP 400 /players: " + msg);
```

Five modules:
- `CORE` — SDK lifecycle, bootstrap, scheduler
- `RELAY` — WebSocket connect/auth/watchdog/disconnect/flush/pending
- `API` — HTTP requests/responses/gzip
- `CACHE` — SSE streams, snapshot parsing
- `TRACK` — all 7 trackers (start/stop/data output/reflection errors)

**Never use `catch (Exception ignored) {}`.** Log the exception with `FteLogger.warn` or `FteLogger.debug`. The only allowed silent catches are best-effort calls that can fail harmlessly (`webSocket.sendClose()` in disconnect, `ws.abort()` in watchdog).

---

## Version compatibility

SDK compiles **once** under 1.21.4 Yarn. One `.jar` works on all versions — Fabric Loader remaps bytecode at runtime.

**Test matrix** at `version-test/build_matrix.py` — auto-fetches latest Yarn + Fabric API versions from Fabric meta API and builds consumer against each:

```bash
cd version-test
python build_matrix.py
```

**Local relay test** at `test_relay_server.py` — simulates backend for watchdog and tracker testing:
```bash
pip install websockets
python test_relay_server.py          # watchdog test (3 snapshots then silent)
python test_relay_server.py --live   # endless snapshots, logs all received payloads
```

---

## Known quirks

### Russian encoding in logs
Windows console must be set to UTF-8 before running:
```powershell
chcp 65001
.\gradlew runClient
```
Or add `-Dfile.encoding=UTF-8` to consumer's JVM args.

### GsonHolder
Single shared Gson instance in `util/GsonHolder.java`. All classes use `GsonHolder.INSTANCE`, never `new Gson()`. If adding Gson deserialization, import `GsonHolder`.

### `findProperty` in build.gradle.kts
The SDK's `build.gradle.kts` uses `findProperty` for ALL version properties. This allows consumer projects to override versions via their own `gradle.properties`. Never use `property()` — use `findProperty()` with fallback.

### `fabric.mod.json` dependencies
Only declares `"minecraft": ">=1.21.0"`. Does NOT declare `fabric-loader` or `fabric-api` — these are provided by the consumer mod. Adding them caused jar-in-jar AccessWidener namespace errors in earlier versions.

### `PlayerNameUtil.extractDonate()`
Single shared utility for extracting donator prefix from TAB player display names. Used by TabTracker, MineTracker, DungeonTracker. Two overloads: `extractDonate(PlayerEntity)` and `extractDonate(PlayerListEntry)`. Do NOT duplicate this logic in individual trackers.

### DungeonTracker optimizations
- Only scans when local player is INSIDE a dungeon zone (checks player position first)
- Uses `((ClientWorld) world).getEntities()` — the method is on ClientWorld, not World
- Caches `getTextMethod` via volatile static field (one-time resolution)

### ServerDetector
- `isFuntime(ip)` — checks `*.funtime.su` or `*.funtime.sh`
- `extractServerId(text)` — parses `Анархия-{n}` from scoreboard sidebar title
- `getServerIdHint()` — reads `ScoreboardDisplaySlot.SIDEBAR`
- Server ID comes from scoreboard, NOT from TAB header (mixin not needed)
