package com.funtimeevents.sdk.api;

import com.funtimeevents.sdk.bootstrap.Bootstrap;
import com.funtimeevents.sdk.event.EventBus;
import com.funtimeevents.sdk.event.FteEvent;
import com.funtimeevents.sdk.net.ApiClient;
import com.funtimeevents.sdk.net.cache.EventCache;
import com.funtimeevents.sdk.net.cache.MineCache;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.TrackerManager;
import com.funtimeevents.sdk.util.FteLogger;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class FunTimeEventsAPI {

    private static FunTimeEventsAPI instance;
    private static ApiClient apiClient;
    private static EventCache eventCache;
    private static MineCache mineCache;

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
            apiClient = new ApiClient(config.baseUrl(), config.apiKey(), config.userAgent());
            sender = apiClient;

            eventCache = new EventCache();
            eventCache.start(apiClient.streamEvents());
            mineCache = new MineCache();
            mineCache.start(apiClient.streamMines());
        }

        TrackerManager trackerManager = new TrackerManager(sender, config);
        FteLogger.info("SDK initialized" + (config.offlineMode() ? " (offline mode)" : ""));
        Bootstrap.getInstance().start(trackerManager, config);
        instance = new FunTimeEventsAPI();
        return instance;
    }

    // --- Local EventBus ---

    public static List<FteEvent> pollEvents() {
        return EventBus.getInstance().drain();
    }

    public static void onEvent(Consumer<FteEvent> listener) {
        EventBus.getInstance().subscribe(listener);
    }

    // --- Backend GET ---

    public static CompletableFuture<String> fetchEvents(Map<String, String> params) {
        return apiClient != null ? apiClient.getEvents(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchMines(Map<String, String> params) {
        return apiClient != null ? apiClient.getMines(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchPlayers(Map<String, String> params) {
        return apiClient != null ? apiClient.getPlayers(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchBans(Map<String, String> params) {
        return apiClient != null ? apiClient.getBans(params) : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchCopperDungeon() {
        return apiClient != null ? apiClient.getCopperDungeon() : CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<String> fetchWardenCity() {
        return apiClient != null ? apiClient.getWardenCity() : CompletableFuture.completedFuture(null);
    }

    // --- SSE Cache ---

    public static Map<String, JsonObject> getCachedEvents() {
        return eventCache != null ? eventCache.getCachedEvents() : Map.of();
    }

    public static Map<String, JsonObject> getCachedMines() {
        return mineCache != null ? mineCache.getCachedMines() : Map.of();
    }

    // --- POST ---

    static void sendTabPlayers(com.funtimeevents.sdk.model.TabPlayersPayload payload) {
        if (apiClient != null) apiClient.sendTabPlayers(payload);
    }

    static void sendBan(com.funtimeevents.sdk.model.BanPayload payload) {
        if (apiClient != null) apiClient.sendBan(payload);
    }

    static void sendCopperDungeon(com.funtimeevents.sdk.model.DungeonPayload payload) {
        if (apiClient != null) apiClient.sendCopperDungeon(payload);
    }

    static void sendWardenCity(com.funtimeevents.sdk.model.DungeonPayload payload) {
        if (apiClient != null) apiClient.sendWardenCity(payload);
    }

    static void sendHellMap(com.funtimeevents.sdk.model.HellMapPayload payload) {
        if (apiClient != null) apiClient.sendHellMap(payload);
    }

    static void sendMinePlayers(com.funtimeevents.sdk.model.MinePlayersAroundPayload payload) {
        if (apiClient != null) apiClient.sendMinePlayers(payload);
    }

    static void sendEventCoordinates(com.funtimeevents.sdk.model.EventCoordinatesPayload payload) {
        if (apiClient != null) apiClient.sendEventCoordinates(payload);
    }

    static void sendCaptcha(com.funtimeevents.sdk.model.CaptchaPayload payload) {
        if (apiClient != null) apiClient.sendCaptcha(payload);
    }
}
