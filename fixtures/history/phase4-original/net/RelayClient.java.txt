package net.funtimeevents.net;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.model.DungeonPayload;
import net.funtimeevents.model.EventCoordinatesPayload;
import net.funtimeevents.model.HellMapPayload;
import net.funtimeevents.model.MinePlayersAroundPayload;
import net.funtimeevents.model.TabPlayersPayload;
import net.funtimeevents.net.cache.RelayCache;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.GsonHolder;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
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
        FteLogger.info(FteLogger.RELAY, "disconnecting");
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
                FteLogger.warn(FteLogger.RELAY, "Connect failed (" + e.getClass().getSimpleName() + "), retrying in " + backoff + "s: " + e.getMessage());
                sleepSeconds(backoff);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_SECONDS);
            }
        }
    }

    private void doConnect() throws Exception {
        authenticated = false;
        lastSnapshotAt = System.currentTimeMillis();
        FteLogger.info(FteLogger.RELAY, "connecting to " + wsUrl + "...");
        CountDownLatch authLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);
        WebSocket.Builder builder = httpClient.newWebSocketBuilder();
        builder.header("User-Agent", userAgent);
        WebSocket ws = builder.buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
            final StringBuilder buffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket ws) {
                webSocket = ws;
                var authMsg = new HashMap<String, Object>();
                authMsg.put("type", "auth");
                if (apiKey != null && !apiKey.isBlank()) {
                    authMsg.put("api_key", apiKey);
                }
                String auth = GSON.toJson(authMsg);
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
                FteLogger.warn(FteLogger.RELAY, "closed: " + statusCode + " " + reason);
                webSocket = null;
                authenticated = false;
                closeLatch.countDown();
                authLatch.countDown();
                if (statusCode == 4001) {
                    FteLogger.error(FteLogger.RELAY, "auth failed, not reconnecting");
                    running = false;
                }
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                FteLogger.warn(FteLogger.RELAY, "error: " + error.getMessage());
                webSocket = null;
                authenticated = false;
                closeLatch.countDown();
                authLatch.countDown();
                WebSocket.Listener.super.onError(ws, error);
            }
        }).get();

        if (!authLatch.await(30, TimeUnit.SECONDS)) {
            FteLogger.warn(FteLogger.RELAY, "auth timeout, reconnecting");
            return;
        }
        if (!authenticated) {
            FteLogger.warn(FteLogger.RELAY, "auth rejected, reconnecting");
            return;
        }

        Thread watchdog = new Thread(() -> {
            try {
                while (!closeLatch.await(1, TimeUnit.SECONDS)) {
                    long idle = System.currentTimeMillis() - lastSnapshotAt;
                    if (idle > IDLE_TIMEOUT_MS) {
                        FteLogger.warn(FteLogger.RELAY, "snapshot watchdog: no snapshots for " + (idle / 1000) + "s, aborting");
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
                FteLogger.debug(FteLogger.RELAY, "received snapshot");
                lastSnapshotAt = System.currentTimeMillis();
                if (cache != null) cache.updateFromSnapshot(msg);
            } else if ("auth_ok".equals(type)) {
                authenticated = true;
                lastSnapshotAt = System.currentTimeMillis();
                FteLogger.info(FteLogger.RELAY, "authenticated");
                authLatch.countDown();
                flushPending();
            }
        } catch (Exception e) {
            FteLogger.warn(FteLogger.RELAY, "failed to parse message: " + e.getMessage());
        }
    }

    private void flushPending() {
        int count = pending.size();
        if (count > 0) {
            FteLogger.info(FteLogger.RELAY, "flushing " + count + " queued messages");
        }
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
                FteLogger.debug(FteLogger.RELAY, "sending " + type);
                sendRaw(msg);
            } else {
                if (pending.isEmpty()) {
                    FteLogger.warn(FteLogger.RELAY, "relay not ready, buffering messages");
                }
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
