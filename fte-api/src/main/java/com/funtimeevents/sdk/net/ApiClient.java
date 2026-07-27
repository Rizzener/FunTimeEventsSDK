package com.funtimeevents.sdk.net;

import com.funtimeevents.sdk.model.BanPayload;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.model.MinePlayersAroundPayload;
import com.funtimeevents.sdk.model.SpawnEventPayload;
import com.funtimeevents.sdk.model.TabPlayersPayload;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.util.FteLogger;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ApiClient implements PayloadSender {

    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final HttpClient httpClient;

    public ApiClient(String baseUrl, String apiKey, String userAgent) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public void sendTabPlayers(TabPlayersPayload payload) {
        postJson("/players", payload);
    }

    @Override
    public void sendBan(BanPayload payload) {
        postJson("/bans", payload);
    }

    @Override
    public void sendCopperDungeon(DungeonPayload payload) {
        postJson("/copper-dungeon", payload);
    }

    @Override
    public void sendWardenCity(DungeonPayload payload) {
        postJson("/warden-city", payload);
    }

    @Override
    public void sendHellMap(HellMapPayload payload) {
        postJson("/events/hell-map", payload);
    }

    @Override
    public void sendMinePlayers(MinePlayersAroundPayload payload) {
        postJson("/mines/players-around", payload);
    }

    @Override
    public void sendSpawnEvent(SpawnEventPayload payload) {
        postJson("/events/spawn", payload);
    }

    private void postJson(String path, Object body) {
        String json = GSON.toJson(body);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .header("User-Agent", userAgent)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        String responseBody = response.body();
                        if (response.statusCode() >= 400) {
                            FteLogger.warn("HTTP " + response.statusCode() + " from " + path + ": " + responseBody);
                        } else {
                            FteLogger.debug("POST " + path + " -> " + response.statusCode() + " " + responseBody);
                        }
                    })
                    .exceptionally(ex -> {
                        FteLogger.warn("Network error sending to " + path + ": " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            FteLogger.warn("Failed to send to " + path + ": " + e.getMessage());
        }
    }
}
