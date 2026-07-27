package com.funtimeevents.sdk.api;

import com.funtimeevents.sdk.bootstrap.Bootstrap;
import com.funtimeevents.sdk.model.BansListResponse;
import com.funtimeevents.sdk.model.CaptchaResponse;
import com.funtimeevents.sdk.model.EventResponse;
import com.funtimeevents.sdk.model.LootAreaResponse;
import com.funtimeevents.sdk.model.MineResponse;
import com.funtimeevents.sdk.model.PlayersListResponse;
import com.funtimeevents.sdk.model.SystemInfo;
import com.funtimeevents.sdk.net.ApiClient;
import com.funtimeevents.sdk.net.RelayClient;
import com.funtimeevents.sdk.net.cache.RelayCache;
import com.funtimeevents.sdk.net.cache.SseCache;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.TrackerManager;
import com.funtimeevents.sdk.util.FteLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    public static FteConfig.Builder builder() {
        return new FteConfig.Builder();
    }

    static FunTimeEventsAPI create(FteConfig.Builder configBuilder) {
        if (instance != null) {
            FteLogger.warn("SDK already initialized, ignoring duplicate call");
            return instance;
        }
        synchronized (FunTimeEventsAPI.class) {
            if (instance != null) {
                FteLogger.warn("SDK already initialized, ignoring duplicate call");
                return instance;
            }
            FteConfig config = new FteConfig(configBuilder);

            if (config.userAgent() == null || config.userAgent().isBlank()) {
                throw new IllegalArgumentException("userAgent is required");
            }
            if (!config.offlineMode() && (config.apiKey() == null || config.apiKey().isBlank())) {
                throw new IllegalArgumentException("apiKey is required in online mode");
            }

            FteLogger.setLevel(config.logLevel());

            PayloadSender sender = null;
            if (!config.offlineMode()) {
                restClient = new ApiClient(config.baseUrl(), config.apiKey(), config.userAgent());

                if (config.wsMode()) {
                    String rawUrl = config.baseUrl();
                    if (rawUrl.endsWith("/")) rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
                    String wsUrl = rawUrl
                            .replace("https://", "wss://")
                            .replace("http://", "ws://") + "/relay";
                    wsClient = new RelayClient(wsUrl, config.apiKey(), config.userAgent());
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
            FteLogger.info("SDK initialized" + (config.offlineMode() ? " (offline mode)" : "")
                    + (config.wsMode() ? " (WSS relay)" : ""));
            Bootstrap.getInstance().start(trackerManager, config);
            instance = new FunTimeEventsAPI();
            return instance;
        }
    }

    // --- Backend GET ---

    public static CompletableFuture<String> fetchEvents(Map<String, String> params) {
        return restClient != null ? restClient.getEvents(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchMines(Map<String, String> params) {
        return restClient != null ? restClient.getMines(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<PlayersListResponse> fetchPlayers(Map<String, String> params) {
        return restClient != null ? restClient.getPlayers(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<BansListResponse> fetchBans(Map<String, String> params) {
        return restClient != null ? restClient.getBans(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchCopperDungeon() {
        return restClient != null ? restClient.getCopperDungeon() : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchWardenCity() {
        return restClient != null ? restClient.getWardenCity() : CompletableFuture.completedFuture(null);
    }

    // --- Cached data (relay snapshots or SSE, ≤5s fresh) ---

    public static List<EventResponse> getEvents() {
        if (relayCache != null) return relayCache.getEvents();
        if (eventCache != null) return eventCache.getData();
        return List.of();
    }

    public static List<MineResponse> getMines() {
        if (relayCache != null) return relayCache.getMines();
        if (mineCache != null) return mineCache.getData();
        return List.of();
    }

    public static List<LootAreaResponse> getCopperDungeons() {
        if (relayCache != null) return relayCache.getCopperDungeons();
        if (copperCache != null) return copperCache.getData();
        return List.of();
    }

    public static List<LootAreaResponse> getWardenCities() {
        if (relayCache != null) return relayCache.getWardenCities();
        if (wardenCache != null) return wardenCache.getData();
        return List.of();
    }

    public static SystemInfo getSystemInfo() {
        if (relayCache != null) return relayCache.getSystemInfo();
        return null;
    }

    // --- POST ---

    public static void sendCaptcha(com.funtimeevents.sdk.model.CaptchaPayload payload) {
        if (restClient != null) restClient.sendCaptcha(payload);
    }

    public static CompletableFuture<CaptchaResponse> solveCaptcha(String base64) {
        if (restClient != null) return restClient.solveCaptcha(base64);
        return CompletableFuture.completedFuture(null);
    }
}
