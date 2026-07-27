package com.funtimeevents.sdk.net.cache;

import com.funtimeevents.sdk.util.FteLogger;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class EventCache {

    private static final Gson GSON = new Gson();

    private final Map<String, JsonObject> cache = new ConcurrentHashMap<>();
    private volatile boolean running;
    private Thread thread;

    public void start(CompletableFuture<HttpResponse<java.io.InputStream>> streamFuture) {
        if (running) return;
        running = true;
        thread = new Thread(() -> readStream(streamFuture), "FTE-EventStream");
        thread.setDaemon(true);
        thread.start();
        FteLogger.info("EventCache started");
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public Map<String, JsonObject> getCachedEvents() {
        return Collections.unmodifiableMap(cache);
    }

    private void readStream(CompletableFuture<HttpResponse<java.io.InputStream>> streamFuture) {
        while (running) {
            try {
                HttpResponse<java.io.InputStream> response = streamFuture.get();
                if (response.statusCode() != 200) {
                    FteLogger.warn("Event stream HTTP " + response.statusCode() + ", retrying in 5s");
                    Thread.sleep(5000);
                    continue;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder data = new StringBuilder();
                    while (running && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            data.append(line.substring(6));
                        } else if (line.isEmpty() && data.length() > 0) {
                            processSseData(data.toString());
                            data.setLength(0);
                        }
                    }
                }
            } catch (Exception e) {
                FteLogger.warn("Event stream error, retrying in 5s: " + e.getMessage());
            }
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        }
        FteLogger.info("EventCache stopped");
    }

    private void processSseData(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (!root.has("data") || root.get("data").isJsonNull()) return;
            JsonArray items = root.getAsJsonArray("data");
            cache.clear();
            for (var item : items) {
                JsonObject event = item.getAsJsonObject();
                String name = event.get("name").getAsString();
                cache.put(name, event);
            }
        } catch (Exception e) {
            FteLogger.debug("SSE event parse error: " + e.getMessage());
        }
    }
}
