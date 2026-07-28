package net.funtimeevents.net;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.model.BansListResponse;
import net.funtimeevents.model.CaptchaPayload;
import net.funtimeevents.model.CaptchaResponse;
import net.funtimeevents.model.DungeonPayload;
import net.funtimeevents.model.HellMapPayload;
import net.funtimeevents.model.MinePlayersAroundPayload;
import net.funtimeevents.model.EventCoordinatesPayload;
import net.funtimeevents.model.PlayersListResponse;
import net.funtimeevents.model.TabPlayersPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.GsonHolder;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
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
import java.util.zip.GZIPOutputStream;

public final class ApiClient implements PayloadSender {

    private static final Gson GSON = GsonHolder.INSTANCE;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final boolean useCompression;
    private final HttpClient httpClient;

    public ApiClient(String baseUrl, String apiKey, String userAgent, boolean useCompression) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.useCompression = useCompression;
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

    // --- GET methods ---

    public CompletableFuture<String> getEvents(Map<String, String> params) {
        return getJson("/events", params);
    }

    public CompletableFuture<String> getMines(Map<String, String> params) {
        return getJson("/mines", params);
    }

    public CompletableFuture<PlayersListResponse> getPlayers(Map<String, String> params) {
        return getJson("/players", params)
                .thenApply(json -> json != null ? GSON.fromJson(json, PlayersListResponse.class) : null);
    }

    public CompletableFuture<BansListResponse> getBans(Map<String, String> params) {
        return getJson("/bans", params)
                .thenApply(json -> json != null ? GSON.fromJson(json, BansListResponse.class) : null);
    }

    public CompletableFuture<String> getCopperDungeon() {
        return getJson("/copper-dungeon", Map.of());
    }

    public CompletableFuture<String> getWardenCity() {
        return getJson("/warden-city", Map.of());
    }

    public CompletableFuture<CaptchaResponse> solveCaptcha(String base64) {
        return postJsonForResponse("/captcha", new CaptchaPayload(base64))
                .thenApply(json -> json != null ? GSON.fromJson(json, CaptchaResponse.class) : null);
    }

    // --- SSE streams ---

    public CompletableFuture<HttpResponse<java.io.InputStream>> streamEvents() {
        return stream("/events/stream");
    }

    public CompletableFuture<HttpResponse<java.io.InputStream>> streamMines() {
        return stream("/mines/stream");
    }

    public CompletableFuture<HttpResponse<java.io.InputStream>> streamCopperDungeons() {
        return stream("/copper-dungeon/stream");
    }

    public CompletableFuture<HttpResponse<java.io.InputStream>> streamWardenCities() {
        return stream("/warden-city/stream");
    }

    // --- Internal ---

    private HttpRequest buildPostRequest(String path, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .header("User-Agent", userAgent)
                .timeout(TIMEOUT);
        if (useCompression) {
            body = gzip(body);
            requestBuilder.header("Content-Encoding", "gzip");
        }
        return requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }

    private static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(data);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            FteLogger.warn(FteLogger.API, "gzip failed: " + e.getMessage());
            return data;
        }
    }

    private CompletableFuture<String> postJsonForResponse(String path, Object body) {
        String json = GSON.toJson(body);
        try {
            return httpClient.sendAsync(buildPostRequest(path, json), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() >= 400) {
                            FteLogger.warn(FteLogger.API, "HTTP " + response.statusCode() + " " + path + ": " + response.body());
                        }
                        return response.body();
                    })
                    .exceptionally(ex -> {
                        FteLogger.warn(FteLogger.API, "Network error from " + path + ": " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            FteLogger.warn(FteLogger.API, "Failed to POST " + path + ": " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    private void postJson(String path, Object body) {
        String json = GSON.toJson(body);
        try {
            httpClient.sendAsync(buildPostRequest(path, json), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        String responseBody = response.body();
                        if (response.statusCode() >= 400) {
                            FteLogger.warn(FteLogger.API, "HTTP " + response.statusCode() + " " + path + ": " + responseBody);
                        } else {
                            FteLogger.debug(FteLogger.API, "POST " + path + " -> " + response.statusCode() + " " + responseBody);
                        }
                    })
                    .exceptionally(ex -> {
                        FteLogger.warn(FteLogger.API, "Network error sending to " + path + ": " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            FteLogger.warn(FteLogger.API, "Failed to send to " + path + ": " + e.getMessage());
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
                            FteLogger.warn(FteLogger.API, "HTTP " + response.statusCode() + " " + path);
                        } else {
                            FteLogger.debug(FteLogger.API, "GET " + path + " -> " + response.statusCode());
                        }
                        return response.body();
                    })
                    .exceptionally(ex -> {
                        FteLogger.warn(FteLogger.API, "Network error from " + path + ": " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            FteLogger.warn(FteLogger.API, "Failed to GET " + path + ": " + e.getMessage());
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
            FteLogger.warn(FteLogger.API, "Failed to stream " + path + ": " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
}
