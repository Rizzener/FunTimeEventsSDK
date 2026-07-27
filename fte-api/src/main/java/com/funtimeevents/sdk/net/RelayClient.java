package com.funtimeevents.sdk.net;

import com.funtimeevents.sdk.model.BanPayload;
import com.funtimeevents.sdk.model.CaptchaPayload;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.EventCoordinatesPayload;
import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.model.MinePlayersAroundPayload;
import com.funtimeevents.sdk.model.TabPlayersPayload;
import com.funtimeevents.sdk.net.cache.RelayCache;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.util.FteLogger;
import com.funtimeevents.sdk.util.GsonHolder;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class RelayClient implements PayloadSender {

    private static final Gson GSON = GsonHolder.INSTANCE;
    private static final int MAX_BACKOFF_SECONDS = 60;
    private static final long IDLE_TIMEOUT_MS = 15_000;

    private final String wsUrl;
    private final String apiKey;
    private final String userAgent;
    private final HttpClient httpClient;
    private final ExecutorService ioExecutor;
    private RelayCache cache;
    private volatile WebSocket webSocket;
    private volatile boolean authenticated;
    private volatile long lastSnapshotAt;
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;
    private Thread reconnectThread;

    public RelayClient(String wsUrl, String apiKey, String userAgent, boolean useCompression) {
        this.wsUrl = wsUrl;
        this.apiKey = apiKey;
        this.userAgent = userAgent;
        this.httpClient = HttpClient.newHttpClient();
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FTE-Relay-IO");
            t.setDaemon(true);
            return t;
        });
    }

    public void setCache(RelayCache cache) {
        this.cache = cache;
    }

    public void connect() {
        reconnectThread = new Thread(this::connectLoop, "FTE-Relay");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    public void disconnect() {
        running = false;
        if (reconnectThread != null) reconnectThread.interrupt();
        if (webSocket != null) {
            try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, ""); } catch (Exception ignored) {}
        }
        ioExecutor.shutdownNow();
    }

    private void connectLoop() {
        int backoff = 1;
        while (running) {
            try {
                doConnect();
                backoff = 1;
            } catch (Exception e) {
                FteLogger.warn("Relay connect failed (" + e.getClass().getSimpleName() + "), retrying in " + backoff + "s: " + e.getMessage());
                sleepSeconds(backoff);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_SECONDS);
            }
        }
    }

    private void doConnect() throws Exception {
        authenticated = false;
        lastSnapshotAt = System.currentTimeMillis();
        FteLogger.info("Relay connecting to " + wsUrl + "...");
        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);
        WebSocket.Builder builder = httpClient.newWebSocketBuilder();
        builder.header("User-Agent", userAgent);
        WebSocket ws = builder.buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
            final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                webSocket = ws;
                String auth = GSON.toJson(Map.of("type", "auth", "api_key", apiKey));
                ws.sendText(auth, true);
                WebSocket.Listener.super.onOpen(ws);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    String msg = buffer.toString();
                    buffer.setLength(0);
                    processMessage(msg, authLatch);
                }
                return WebSocket.Listener.super.onText(ws, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                FteLogger.warn("Relay closed: " + statusCode + " " + reason);
                webSocket = null;
                authenticated = false;
                closeLatch.countDown();
                authLatch.countDown();
                if (statusCode == 4001) {
                    FteLogger.error("Relay auth failed, not reconnecting");
                    running = false;
                }
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                FteLogger.warn("Relay error: " + error.getMessage());
                webSocket = null;
                authenticated = false;
                closeLatch.countDown();
                authLatch.countDown();
                WebSocket.Listener.super.onError(ws, error);
            }
        }).get();

        if (!authLatch.await(30, TimeUnit.SECONDS)) {
            FteLogger.warn("Relay auth timeout, reconnecting");
            return;
        }
        if (!authenticated) {
            FteLogger.warn("Relay auth rejected, reconnecting");
            return;
        }

        Thread watchdog = new Thread(() -> {
            try {
                while (!closeLatch.await(1, TimeUnit.SECONDS)) {
                    long idle = System.currentTimeMillis() - lastSnapshotAt;
                    if (idle > IDLE_TIMEOUT_MS) {
                        FteLogger.warn("Relay snapshot watchdog: no snapshots for " + (idle / 1000) + "s, aborting");
                        try { ws.abort(); } catch (Exception ignored) {}
                        webSocket = null;
                        authenticated = false;
                        closeLatch.countDown();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "FTE-Relay-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        closeLatch.await();
    }

    private void processMessage(String msg, CountDownLatch authLatch) {
        try {
            Map<String, Object> parsed = GSON.fromJson(msg, Map.class);
            String type = (String) parsed.get("type");
            if ("snapshot".equals(type)) {
                lastSnapshotAt = System.currentTimeMillis();
                if (cache != null) cache.updateFromSnapshot(msg);
            } else if ("auth_ok".equals(type)) {
                authenticated = true;
                lastSnapshotAt = System.currentTimeMillis();
                FteLogger.info("Relay authenticated");
                authLatch.countDown();
                flushPending();
            }
        } catch (Exception e) {
            FteLogger.warn("Relay failed to parse message: " + e.getMessage());
        }
    }

    private void flushPending() {
        String msg;
        while ((msg = pending.poll()) != null) {
            sendRaw(msg);
        }
    }

    private void sendRaw(String json) {
        if (webSocket != null && authenticated) {
            webSocket.sendText(json, true);
        }
    }

    private void sendMessage(String type, Object body) {
        ioExecutor.execute(() -> {
            String msg = GSON.toJson(Map.of("type", type, "body", body));
            if (authenticated && webSocket != null) {
                sendRaw(msg);
            } else {
                pending.add(msg);
            }
        });
    }

    private void sleepSeconds(int seconds) {
        try { Thread.sleep(seconds * 1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // --- PayloadSender ---

    @Override public void sendTabPlayers(TabPlayersPayload p) { sendMessage("players", p); }
    @Override public void sendBan(BanPayload p) { sendMessage("bans", p); }
    @Override public void sendCopperDungeon(DungeonPayload p) { sendMessage("copper_dungeon", p); }
    @Override public void sendWardenCity(DungeonPayload p) { sendMessage("warden_city", p); }
    @Override public void sendHellMap(HellMapPayload p) { sendMessage("hell_map", p); }
    @Override public void sendEventCoordinates(EventCoordinatesPayload p) { sendMessage("coordinates", p); }
    @Override public void sendMinePlayers(MinePlayersAroundPayload p) { sendMessage("players_around", p); }
}
