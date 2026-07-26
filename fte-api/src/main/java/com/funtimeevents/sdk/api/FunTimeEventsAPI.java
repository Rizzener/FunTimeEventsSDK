package com.funtimeevents.sdk.api;

import com.funtimeevents.sdk.bootstrap.Bootstrap;
import com.funtimeevents.sdk.event.EventBus;
import com.funtimeevents.sdk.event.FteEvent;
import com.funtimeevents.sdk.model.BanPayload;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.model.MinePlayersAroundPayload;
import com.funtimeevents.sdk.model.SpawnEventPayload;
import com.funtimeevents.sdk.model.TabPlayersPayload;
import com.funtimeevents.sdk.net.ApiClient;
import com.funtimeevents.sdk.util.FteLogger;

import java.util.List;
import java.util.function.Consumer;

public final class FunTimeEventsAPI {

    private static String baseUrl;
    private static String apiKey;
    private static boolean offlineMode;
    private static boolean initialized;
    private static ApiClient apiClient;

    private FunTimeEventsAPI() {
    }

    public static void init(String baseUrl, String apiKey) {
        init(baseUrl, apiKey, false);
    }

    public static void init(String baseUrl, String apiKey, boolean offlineMode) {
        if (initialized) {
            FteLogger.warn("SDK already initialized, ignoring duplicate call");
            return;
        }
        if (baseUrl != null && baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        if (apiKey != null && apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        FunTimeEventsAPI.baseUrl = baseUrl;
        FunTimeEventsAPI.apiKey = apiKey;
        FunTimeEventsAPI.offlineMode = offlineMode;
        initialized = true;

        if (!offlineMode && baseUrl != null && apiKey != null) {
            apiClient = new ApiClient(baseUrl, apiKey);
        }

        FteLogger.info("SDK initialized" + (offlineMode ? " (offline mode)" : ""));
        Bootstrap.getInstance().start();
    }

    public static void init(String apiKey) {
        init(null, apiKey);
    }

    public static void init() {
        init(null, null, true);
    }

    public static List<FteEvent> getEvents() {
        return EventBus.getInstance().drain();
    }

    public static void onEvent(Consumer<FteEvent> listener) {
        EventBus.getInstance().subscribe(listener);
    }

    public static void sendTabPlayers(TabPlayersPayload payload) {
        if (apiClient != null) {
            apiClient.sendTabPlayers(payload);
        }
    }

    public static void sendBan(BanPayload payload) {
        if (apiClient != null) {
            apiClient.sendBan(payload);
        }
    }

    public static void sendCopperDungeon(DungeonPayload payload) {
        if (apiClient != null) {
            apiClient.sendCopperDungeon(payload);
        }
    }

    public static void sendWardenCity(DungeonPayload payload) {
        if (apiClient != null) {
            apiClient.sendWardenCity(payload);
        }
    }

    public static void sendHellMap(HellMapPayload payload) {
        if (apiClient != null) {
            apiClient.sendHellMap(payload);
        }
    }

    public static void sendMinePlayers(MinePlayersAroundPayload payload) {
        if (apiClient != null) {
            apiClient.sendMinePlayers(payload);
        }
    }

    public static void sendSpawnEvent(SpawnEventPayload payload) {
        if (apiClient != null) {
            apiClient.sendSpawnEvent(payload);
        }
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static String getApiKey() {
        return apiKey;
    }

    public static boolean isOfflineMode() {
        return offlineMode;
    }
}
