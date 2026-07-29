package net.funtimeevents.api;

import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.bootstrap.Bootstrap;
import net.funtimeevents.model.BansListResponse;
import net.funtimeevents.model.CaptchaResponse;
import net.funtimeevents.model.EventResponse;
import net.funtimeevents.model.LootAreaResponse;
import net.funtimeevents.model.MineResponse;
import net.funtimeevents.model.PlayersListResponse;
import net.funtimeevents.model.SystemInfo;
import net.funtimeevents.net.ApiClient;
import net.funtimeevents.net.RelayClient;
import net.funtimeevents.net.cache.RelayCache;
import net.funtimeevents.net.cache.SseCache;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.TrackerManager;
import net.funtimeevents.util.FteLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point for the FunTimeEvents SDK.
 *
 * <p>Create and configure the SDK once on mod startup, then use
 * the static methods to access cached data or make backend requests.
 *
 * <pre>{@code
 * FunTimeEventsAPI.builder()
 *     .userAgent("MyMod/1.0")
 *     // .apiKey("sk-fte-...")    // optional
 *     .build();
 * }</pre>
 */
public final class FunTimeEventsAPI {

    private static volatile FunTimeEventsAPI instance;
    private static ApiClient restClient;
    private static RelayClient wsClient;
    private static SseCache<EventResponse> eventCache;
    private static SseCache<MineResponse> mineCache;
    private static SseCache<LootAreaResponse> copperCache;
    private static SseCache<LootAreaResponse> wardenCache;
    private static RelayCache relayCache;

    private FunTimeEventsAPI() {
    }

    /**
     * Returns a new {@link FteConfig.Builder} — the only way
     * to configure and initialise the SDK.
     */
    public static FteConfig.Builder builder() {
        return new FteConfig.Builder();
    }

    /**
     * Returns the SDK version (e.g. {@code "1.0.1"}).
     *
     * <p>Reads from {@code fabric.mod.json} at runtime.
     *
     * @return the version string, or {@code "unknown"} if the mod container is not found
     */
    public static String getVersion() {
        return FabricLoader.getInstance()
                .getModContainer("fte-api")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    static FunTimeEventsAPI create(FteConfig.Builder configBuilder) {
        if (instance != null) {
            FteLogger.warn(FteLogger.CORE, "SDK already initialized, ignoring duplicate call");
            return instance;
        }
        synchronized (FunTimeEventsAPI.class) {
            if (instance != null) {
                FteLogger.warn(FteLogger.CORE, "SDK already initialized, ignoring duplicate call");
                return instance;
            }
            FteConfig config = new FteConfig(configBuilder);

            if (config.userAgent() == null || config.userAgent().isBlank()) {
                throw new IllegalArgumentException("userAgent is required");
            }
            if (!config.offlineMode() && (config.apiKey() == null || config.apiKey().isBlank())) {
                FteLogger.warn(FteLogger.CORE, "apiKey not set — relying on proxy for auth");
            }

            FteLogger.setLevel(config.logLevel());

            PayloadSender sender = null;
            if (!config.offlineMode()) {
                restClient = new ApiClient(config.baseUrl(), config.apiKey(), config.userAgent(), config.compression());

                if (config.wsMode()) {
                    String rawUrl = config.baseUrl();
                    if (rawUrl.endsWith("/")) rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
                    String wsUrl = rawUrl
                            .replace("https://", "wss://")
                            .replace("http://", "ws://") + "/relay";
                    wsClient = new RelayClient(wsUrl, config.apiKey(), config.userAgent(), config.compression());
                    relayCache = new RelayCache();
                    wsClient.setCache(relayCache);
                    wsClient.connect();
                    sender = wsClient;
                } else {
                    sender = restClient;
                    eventCache = new SseCache<>("FTE-EventStream", EventResponse.class, EventResponse::name);
                    eventCache.start(restClient.streamEvents());
                    mineCache = new SseCache<>("FTE-MineStream", MineResponse.class, m -> m.serverId() + "_" + m.rarity());
                    mineCache.start(restClient.streamMines());
                    copperCache = new SseCache<>("FTE-CopperStream", LootAreaResponse.class, l -> String.valueOf(l.serverId()));
                    copperCache.start(restClient.streamCopperDungeons());
                    wardenCache = new SseCache<>("FTE-WardenStream", LootAreaResponse.class, l -> String.valueOf(l.serverId()));
                    wardenCache.start(restClient.streamWardenCities());
                }
            }

            TrackerManager trackerManager = new TrackerManager(sender, config);
            StringBuilder mode = new StringBuilder();
            if (config.offlineMode()) mode.append(" offline");
            if (config.wsMode()) mode.append(" wss"); else mode.append(" rest");
            if (config.compression()) mode.append(" gzip");
            if (mode.length() == 0) mode.append(" default");
            FteLogger.info(FteLogger.CORE, "SDK initialized —" + mode
                    + " — trackers {" + (config.tabPlayersEnabled() ? " tab" : "")
                    + (config.bansEnabled() ? " bans" : "")
                    + (config.dungeonEnabled() ? " dungeon" : "")
                    + (config.hellMapEnabled() ? " hellmap" : "")
                    + (config.mineEnabled() ? " mine" : "")
                    + (config.coordinatesEnabled() ? " coords" : "") + " }");
            Bootstrap.getInstance().start(trackerManager, config);
            instance = new FunTimeEventsAPI();
            return instance;
        }
    }

    // --- Backend GET ---

    /**
     * Fetches active events from the backend.
     *
     * @param params optional query parameters (e.g. {@code Map.of("server_id", "4")})
     * @return JSON response as a raw string
     * @see #getEvents() for a cached, typed view
     */
    public static CompletableFuture<String> fetchEvents(Map<String, String> params) {
        return restClient != null ? restClient.getEvents(params) : CompletableFuture.completedFuture(null);
    }

    /**
     * Fetches active mines from the backend.
     *
     * @param params optional query parameters
     * @return JSON response as a raw string
     * @see #getMines() for a cached, typed view
     */
    public static CompletableFuture<String> fetchMines(Map<String, String> params) {
        return restClient != null ? restClient.getMines(params) : CompletableFuture.completedFuture(null);
    }

    /**
     * Fetches player metadata from the backend.
     *
     * @param params query parameters (e.g. {@code Map.of("player_name", "Steve")})
     * @return typed response with per-server history for the player
     */
    public static CompletableFuture<PlayersListResponse> fetchPlayers(Map<String, String> params) {
        return restClient != null ? restClient.getPlayers(params) : CompletableFuture.completedFuture(null);
    }

    /**
     * Fetches recent ban records from the backend.
     *
     * @param params optional query parameters (e.g. {@code Map.of("limit", "10")})
     * @return typed response with ban list
     */
    public static CompletableFuture<BansListResponse> fetchBans(Map<String, String> params) {
        return restClient != null ? restClient.getBans(params) : CompletableFuture.completedFuture(null);
    }

    /**
     * Fetches copper dungeon state from the backend.
     *
     * @return JSON response as a raw string
     * @see #getCopperDungeons() for a cached, typed view
     */
    public static CompletableFuture<String> fetchCopperDungeon() {
        return restClient != null ? restClient.getCopperDungeon() : CompletableFuture.completedFuture(null);
    }

    /**
     * Fetches Warden City dungeon state from the backend.
     *
     * @return JSON response as a raw string
     * @see #getWardenCities() for a cached, typed view
     */
    public static CompletableFuture<String> fetchWardenCity() {
        return restClient != null ? restClient.getWardenCity() : CompletableFuture.completedFuture(null);
    }

    // --- Cached data (relay snapshots or SSE, ≤5s fresh) ---

    /**
     * Returns locally cached active events, updated automatically
     * from the relay (WSS mode) or SSE stream (REST mode).
     *
     * @return never {@code null}; empty list when no data is available
     */
    public static List<EventResponse> getEvents() {
        if (relayCache != null) return relayCache.getEvents();
        if (eventCache != null) return eventCache.getData();
        return List.of();
    }

    /**
     * Returns locally cached active mines, updated automatically
     * from the relay or SSE stream.
     *
     * @return never {@code null}; empty list when no data is available
     */
    public static List<MineResponse> getMines() {
        if (relayCache != null) return relayCache.getMines();
        if (mineCache != null) return mineCache.getData();
        return List.of();
    }

    /**
     * Returns locally cached copper dungeon data,
     * updated automatically from the relay or SSE stream.
     *
     * @return never {@code null}; empty list when no data is available
     */
    public static List<LootAreaResponse> getCopperDungeons() {
        if (relayCache != null) return relayCache.getCopperDungeons();
        if (copperCache != null) return copperCache.getData();
        return List.of();
    }

    /**
     * Returns locally cached Warden City data,
     * updated automatically from the relay or SSE stream.
     *
     * @return never {@code null}; empty list when no data is available
     */
    public static List<LootAreaResponse> getWardenCities() {
        if (relayCache != null) return relayCache.getWardenCities();
        if (wardenCache != null) return wardenCache.getData();
        return List.of();
    }

    /**
     * Returns backend system info (events / mines / dungeon / client counts).
     * Available only in WSS relay mode.
     *
     * @return the info object, or {@code null} if not connected in WSS mode
     */
    public static SystemInfo getSystemInfo() {
        if (relayCache != null) return relayCache.getSystemInfo();
        return null;
    }

    // --- POST ---

    /**
     * Sends a base64-encoded screenshot to the backend for captcha solving.
     *
     * @param base64 the PNG screenshot as a base64 string
     * @return the solve result (solved text + confidence), or {@code null} on failure
     */
    public static CompletableFuture<CaptchaResponse> solveCaptcha(String base64) {
        if (restClient != null) return restClient.solveCaptcha(base64);
        return CompletableFuture.completedFuture(null);
    }
}
