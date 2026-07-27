package com.funtimeevents.sdk.net.cache;

import com.funtimeevents.sdk.util.GsonHolder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class SseCache<T> {

    private static final Gson GSON = GsonHolder.INSTANCE;
    private static final double STALE_SECONDS = 5.0;

    private final Map<String, T> store = new ConcurrentHashMap<>();
    private final String threadName;
    private final Class<T> type;
    private final Function<T, String> keyExtractor;
    private volatile long lastUpdateNanos;
    private volatile boolean running;
    private Thread thread;

    public SseCache(String threadName, Class<T> type, Function<T, String> keyExtractor) {
        this.threadName = threadName;
        this.type = type;
        this.keyExtractor = keyExtractor;
    }

    public void start(CompletableFuture<HttpResponse<java.io.InputStream>> streamFuture) {
        if (running) return;
        running = true;
        thread = new Thread(() -> readStream(streamFuture), threadName);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) { thread.interrupt(); thread = null; }
    }

    public List<T> getData() {
        if (isStale()) { store.clear(); return List.of(); }
        return List.copyOf(store.values());
    }

    private boolean isStale() {
        return (System.nanoTime() - lastUpdateNanos) / 1_000_000_000.0 > STALE_SECONDS;
    }

    private void readStream(CompletableFuture<HttpResponse<java.io.InputStream>> streamFuture) {
        while (running) {
            try {
                HttpResponse<java.io.InputStream> response = streamFuture.get();
                if (response.statusCode() != 200) { Thread.sleep(5000); continue; }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder data = new StringBuilder();
                    while (running && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) data.append(line.substring(6));
                        else if (line.isEmpty() && data.length() > 0) {
                            process(data.toString());
                            data.setLength(0);
                        }
                    }
                }
            } catch (Exception e) { try { Thread.sleep(5000); } catch (InterruptedException ignored) {} }
        }
    }

    private void process(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (!root.has("data") || root.get("data").isJsonNull()) return;
            JsonArray items = root.getAsJsonArray("data");
            store.clear();
            for (var item : items) {
                T obj = GSON.fromJson(item, type);
                store.put(keyExtractor.apply(obj), obj);
            }
            lastUpdateNanos = System.nanoTime();
        } catch (Exception ignored) {}
    }
}
