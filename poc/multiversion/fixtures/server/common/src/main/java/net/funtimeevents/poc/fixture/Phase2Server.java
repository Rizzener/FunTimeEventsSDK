package net.funtimeevents.poc.fixture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Real local Fabric dedicated server: fixtures cross the normal packet codecs. */
public final class Phase2Server implements ModInitializer {
    private final Map<UUID, Run> runs = new HashMap<>();
    private volatile int joins;
    private final JsonArray sessions = JsonParser.parseReader(new InputStreamReader(
        java.util.Objects.requireNonNull(getClass().getResourceAsStream("/phase2/ban-sessions.json")),
        StandardCharsets.UTF_8)).getAsJsonArray();

    private static final class Run {
        final ServerPlayer player;
        final int session;
        volatile int ticks;
        Run(ServerPlayer player, int session) { this.player = player; this.session = session; }
    }

    @Override public void onInitialize() {
        if (Boolean.getBoolean("fte.phase3")) { new Phase3Server().onInitialize(); return; }
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            int session = ++joins;
            if (session > sessions.size()) throw new IllegalStateException("Unexpected extra connection");
            var board = server.getScoreboard();
            board.setDisplayObjective(DisplaySlot.SIDEBAR, null);
            for (var objective : java.util.List.copyOf(board.getObjectives())) board.removeObjective(objective);
            runs.put(handler.player.getUUID(), new Run(handler.player, session));
            System.out.println("[PHASE2-SERVER] JOIN session=" + session);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            server.execute(() -> runs.remove(handler.player.getUUID()));
            System.out.println("[PHASE2-SERVER] DISCONNECT");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Files.exists(Path.of("phase2.stop"))) { server.halt(false); return; }
            for (Run run : java.util.List.copyOf(runs.values())) {
                int tick = ++run.ticks;
                for (var element : sessions.get(run.session - 1).getAsJsonObject().getAsJsonArray("actions")) {
                    JsonObject action = element.getAsJsonObject();
                    if (action.get("at").getAsInt() != tick) continue;
                    if (action.get("kind").getAsString().equals("sidebar")) {
                        var board = server.getScoreboard();
                        var obj = board.addObjective("phase2_" + run.session + "_" + tick,
                            ObjectiveCriteria.DUMMY, Component.literal(action.get("title").getAsString()),
                            ObjectiveCriteria.RenderType.INTEGER, false, null);
                        board.setDisplayObjective(DisplaySlot.SIDEBAR, obj);
                        System.out.println("[PHASE2-SERVER] SIDEBAR session=" + run.session + " title=" + obj.getDisplayName().getString());
                    } else {
                        Component message = component(action);
                        String channel = action.get("channel").getAsString();
                        if (channel.equals("CHAT")) {
                            run.player.connection.send(new ClientboundDisguisedChatPacket(message,
                                ChatType.bind(ChatType.CHAT, run.player.registryAccess(), Component.literal("Fixture"))));
                        } else {
                            run.player.sendSystemMessage(message);
                        }
                        System.out.println("[PHASE2-SERVER] SEND session=" + run.session + " id="
                            + action.get("id").getAsString() + " channel=" + channel);
                    }
                }
                if (tick == 90) run.player.sendSystemMessage(Component.literal("PHASE2_END " + run.session));
                if (tick == 100) run.player.connection.disconnect(Component.literal("Phase2 session complete"));
            }
        });
    }

    private static Component component(JsonObject fixture) {
        String plain = fixture.get("text").getAsString();
        if (!fixture.has("hover")) return Component.literal(plain);
        Component hover = Component.literal(fixture.get("hover").getAsString());
        String placement = fixture.get("placement").getAsString();
        if (placement.equals("root")) return FixtureHover.attach(Component.literal(plain), hover);
        String suffix = "[Подробнее]";
        MutableComponent result = Component.literal(plain.substring(0, plain.length() - suffix.length()));
        MutableComponent child = FixtureHover.attach(Component.literal(suffix), hover);
        return result.append(placement.equals("nested") ? Component.literal("").append(child) : child);
    }
}
