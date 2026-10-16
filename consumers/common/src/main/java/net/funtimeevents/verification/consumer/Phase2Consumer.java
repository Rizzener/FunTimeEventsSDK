package net.funtimeevents.verification.consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.model.BanPayload;
import net.funtimeevents.poc.api.BanSliceAPI;
import net.funtimeevents.internal.platform.MinecraftText;
import net.funtimeevents.internal.game.GameSessionRuntime;
import net.funtimeevents.util.GsonHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Separate test driver. Never invokes SDK parsers or Fabric event invokers. */
public final class Phase2Consumer {
    private final JsonArray sessions = JsonParser.parseReader(new InputStreamReader(
        java.util.Objects.requireNonNull(getClass().getResourceAsStream("/phase2/ban-sessions.json")),
        StandardCharsets.UTF_8)).getAsJsonArray();
    private final JsonArray payloads = new JsonArray();
    private final JsonArray expected = new JsonArray();
    private final JsonArray observations = new JsonArray();
    private final JsonArray lifecycle = new JsonArray();
    private final JsonArray disconnectThreads = new JsonArray();
    private final Map<String, Integer> seen = new HashMap<>();
    private final ConcurrentLinkedQueue<String> pendingDisconnects = new ConcurrentLinkedQueue<>();
    private final Consumer<BanPayload> sink = payload -> {
        var json = GsonHolder.INSTANCE.toJsonTree(payload);
        payloads.add(json);
        System.out.println("[PHASE2] PAYLOAD " + json);
    };
    private final GameSessionRuntime runtime = BanSliceAPI.start(sink);
    private volatile int ticks;
    private volatile int currentSession;
    private volatile int reconnectDelay = -1;
    private volatile boolean ended;
    private volatile boolean finished;

    public static void start() { new Phase2Consumer().install(); }

    private void install() {
        var loader = FabricLoader.getInstance();
        require(!loader.isDevelopmentEnvironment(), "Not a production run");
        require(loader.getModContainer("fte-api").orElseThrow().getOrigin().getKind().name().equals("NESTED"), "SDK not nested");
        require(BanSliceAPI.start(sink) == runtime && BanSliceAPI.bindings() == 1, "Duplicate binding");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> observe("GAME", message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, timestamp) -> observe("CHAT", message));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            require(runtime.isJoined() && runtime.joins() == currentSession, "JOIN lifecycle missing/duplicated");
            require(BanSliceAPI.start(sink) == runtime && BanSliceAPI.bindings() == 1, "Reconnected with duplicate subscriptions");
            lifecycle.add("JOIN " + currentSession);
            System.out.println("[PHASE2] JOIN session=" + currentSession + " host=" + runtime.state().serverIp());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            String sourceThread = Thread.currentThread().getName();
            System.out.println("[PHASE2] DISCONNECT_RAW thread=" + sourceThread);
            pendingDisconnects.add(sourceThread);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) return;
            String sourceThread;
            while ((sourceThread = pendingDisconnects.poll()) != null) {
                require(client.isSameThread(), "DISCONNECT was not normalized onto client thread");
                require(ended, "Unexpected disconnect before fixtures completed");
                require(!runtime.isJoined() && runtime.state().serverId() == -1 && runtime.state().serverIp() == null && !runtime.state().onFuntime(), "State not cleared");
                require(runtime.disconnects() == currentSession, "Missing/duplicate DISCONNECT");
                verifySession();
                lifecycle.add("DISCONNECT " + currentSession + " cleared");
                var threads = new JsonObject();
                threads.addProperty("source", sourceThread);
                threads.addProperty("handled", Thread.currentThread().getName());
                disconnectThreads.add(threads);
                System.out.println("[PHASE2] DISCONNECT session=" + currentSession + " state=CLEARED bindings=" + BanSliceAPI.bindings());
                reconnectDelay = 40;
            }
            if (++ticks > 6000) throw new IllegalStateException("Phase2 client timeout");
            if (currentSession == 0 && ticks >= 100) connect();
            else if (reconnectDelay >= 0 && --reconnectDelay == 0) {
                if (currentSession == sessions.size()) finish(client);
                else connect();
            }
        });
    }

    private void connect() {
        currentSession++;
        ended = false;
        reconnectDelay = -1;
        var client = Minecraft.getInstance();
        String logicalHost = sessions.get(currentSession - 1).getAsJsonObject().get("host").getAsString();
        var data = new ServerData("Phase2 fixture " + currentSession, logicalHost, ServerData.Type.OTHER);
        // Real transport to loopback, logical ServerData address exercises the unmodified host matcher.
        ConnectScreen.startConnecting(new TitleScreen(), client,
            ServerAddress.parseString("127.0.0.1:" + Integer.getInteger("fte.phase2.port")), data, false, null);
    }

    private void observe(String channel, Component component) {
        if (currentSession == 0 || finished) return;
        String plain = component.getString();
        if (plain.equals("PHASE2_END " + currentSession)) { ended = true; return; }
        for (var item : sessions.get(currentSession - 1).getAsJsonObject().getAsJsonArray("actions")) {
            var action = item.getAsJsonObject();
            if (!action.get("kind").getAsString().equals("message") || !plain.contains(action.get("text").getAsString())) continue;
            String key = currentSession + ":" + action.get("id").getAsString();
            int count = seen.merge(key, 1, Integer::sum);
            require(count == 1, "Duplicate Fabric callback: " + key);
            require(channel.equals(action.get("channel").getAsString()), "Wrong Fabric callback channel");
            String hover = MinecraftText.hover(component);
            require(hover.equals(action.has("hover") ? action.get("hover").getAsString() : ""), "Hover mismatch: " + key + ": " + hover);
            var row = new JsonObject();
            row.addProperty("session", currentSession);
            row.addProperty("id", action.get("id").getAsString());
            row.addProperty("channel", channel);
            row.addProperty("plain", plain);
            row.addProperty("hover", hover);
            row.addProperty("server_id", runtime.state().serverId());
            row.addProperty("on_funtime", runtime.state().onFuntime());
            observations.add(row);
            System.out.println("[PHASE2] CALLBACK " + row);
        }
    }

    private void verifySession() {
        for (var item : sessions.get(currentSession - 1).getAsJsonObject().getAsJsonArray("actions")) {
            var action = item.getAsJsonObject();
            if (!action.get("kind").getAsString().equals("message")) continue;
            String key = currentSession + ":" + action.get("id").getAsString();
            require(seen.getOrDefault(key, 0) == 1, "Fixture was not delivered: " + key);
            if (!action.get("payload").isJsonNull()) expected.add(action.get("payload"));
        }
        require(payloads.equals(expected), "Payload mismatch: " + payloads + " != " + expected);
    }

    private void finish(Minecraft client) {
        require(runtime.joins() == 4 && runtime.disconnects() == 4 && BanSliceAPI.bindings() == 1, "Lifecycle totals");
        var result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("target", System.getProperty("fte.phase2.minecraft"));
        result.addProperty("java", Runtime.version().feature());
        result.addProperty("namespace", FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace());
        result.addProperty("development", false);
        result.addProperty("origin", "NESTED");
        result.addProperty("bindings", BanSliceAPI.bindings());
        result.addProperty("joins", runtime.joins());
        result.addProperty("disconnects", runtime.disconnects());
        result.addProperty("ticks", runtime.ticks());
        result.addProperty("sdk_messages", runtime.messages());
        result.add("lifecycle", lifecycle);
        result.add("disconnect_threads", disconnectThreads);
        result.add("callbacks", observations);
        result.add("payloads", payloads);
        try { Files.writeString(Path.of("phase2-result.json"), GsonHolder.INSTANCE.toJson(result), StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot save results", e); }
        finished = true;
        System.out.println("[PHASE2] PASS sessions=4 payloads=" + payloads.size() + " callbacks=" + observations.size());
        client.stop();
    }

    private static void require(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
    }
}
