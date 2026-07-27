package com.funtimeevents.sdk.net;

import com.funtimeevents.sdk.model.BanPayload;
import com.funtimeevents.sdk.model.CaptchaPayload;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.model.MinePlayersAroundPayload;
import com.funtimeevents.sdk.model.EventCoordinatesPayload;
import com.funtimeevents.sdk.model.TabPlayersPayload;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.util.FteLogger;
import com.google.gson.Gson;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    // --- POST methods ---

    @Override
    public void sendTabPlayers(TabPlayersPayload payload) { postJson("/players", payload); }
    @Override
    public void sendBan(BanPayload payload) { postJson("/bans", payload); }
    @Override
    public void sendCopperDungeon(DungeonPayload payload) { postJson("/copper-dungeon", payload); }
    @Override
    public void sendWardenCity(DungeonPayload payload) { postJson("/warden-city", payload); }
    @Override
    public void sendHellMap(HellMapPayload payload) { postJson("/events/hell-map", payload); }
    @Override
    public void sendMinePlayers(MinePlayersAroundPayload payload) { postJson("/mines/players-around", payload); }
    @Override
    public void sendEventCoordinates(EventCoordinatesPayload payload) { postJson("/events/coordinates", payload); }
    @Override
    public void sendCaptcha(CaptchaPayload payload) { postJson("/captcha", payload); }

    // --- GET methods ---

    public CompletableFuture<String> getEvents(Map<String, String> params) {
        return getJson("/events", params);
    }

    public CompletableFuture<String> getMines(Map<String, String> params) {
        return getJson("/mines", params);
    }

    public CompletableFuture<String> getPlayers(Map<String, String> params) {
        return getJson("/players", params);
    }

    public CompletableFuture<String> getBans(Map<String, String> params) {
        return getJson("/bans", params);
    }

    public CompletableFuture<String> getCopperDungeon() {
        return getJson("/copper-dungeon", Map.of());
    }

    public CompletableFuture<String> getWardenCity() {
        return getJson("/warden-city", Map.of());
    }

    // --- SSE streams ---

    public CompletableFuture<HttpResponse<java.io.InputStream>> streamEvents() {
        return stream("/events/stream");
    }

    public CompletableFuture<HttpResponse<java.io.InputStream>> streamMines() {
        return stream("/mines/stream");
    }

    // --- Internal ---

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

    private CompletableFuture<String> getJson(String path, Map<String, String> params) {
        try {
            String query = params.entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            String uri = baseUrl + path + (query.isEmpty() ? "" : "?" + query);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("X-API-Key", apiKey)
                    .header("User-Agent", userAgent)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() >= 400) {
                            FteLogger.warn("HTTP " + response.statusCode() + " from " + path);
                        }
                        return response.body();
                    })
                    .exceptionally(ex -> {
                        FteLogger.warn("Network error from " + path + ": " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            FteLogger.warn("Failed to GET " + path + ": " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<HttpResponse<java.io.InputStream>> stream(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("X-API-Key", apiKey)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/event-stream")
                    .timeout(Duration.ZERO)
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            FteLogger.warn("Failed to stream " + path + ": " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
}
