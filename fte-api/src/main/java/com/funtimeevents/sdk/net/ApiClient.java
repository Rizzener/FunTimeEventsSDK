package com.funtimeevents.sdk.net;

import com.funtimeevents.sdk.event.FteEvent;
import com.funtimeevents.sdk.model.BanPayload;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.model.MinePlayersAroundPayload;
import com.funtimeevents.sdk.model.TabPlayersPayload;
import com.funtimeevents.sdk.util.FteLogger;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ApiClient {

    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public ApiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIMEOUT)
                .build();
    }

    public CompletableFuture<Void> sendEvents(List<FteEvent> events) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return postJson("/events", new EventsPayload(events));
    }

    public CompletableFuture<Void> sendTabPlayers(TabPlayersPayload payload) {
        return postJson("/players", payload);
    }

    public CompletableFuture<Void> sendBan(BanPayload payload) {
        return postJson("/bans", payload);
    }

    public CompletableFuture<Void> sendCopperDungeon(DungeonPayload payload) {
        return postJson("/copper-dungeon", payload);
    }

    public CompletableFuture<Void> sendWardenCity(DungeonPayload payload) {
        return postJson("/warden-city", payload);
    }

    public CompletableFuture<Void> sendHellMap(HellMapPayload payload) {
        return postJson("/events/hell-map", payload);
    }

    public CompletableFuture<Void> sendMinePlayers(MinePlayersAroundPayload payload) {
        return postJson("/mines/players-around", payload);
    }

    private CompletableFuture<Void> postJson(String path, Object body) {
        String json = GSON.toJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        String responseBody = response.body();
                        FteLogger.warn("HTTP " + response.statusCode() + " from " + path + ": " + responseBody);
                        throw new RuntimeException("HTTP " + response.statusCode() + ": " + responseBody);
                    }
                })
                .exceptionallyCompose(ex -> {
                    if (!(ex.getCause() instanceof RuntimeException)) {
                        FteLogger.warn("Network error sending to " + path + ": " + ex.getMessage());
                    }
                    return CompletableFuture.failedFuture(ex);
                });
    }

    @SuppressWarnings("unused")
    private record EventsPayload(List<FteEvent> events) {
    }
}
