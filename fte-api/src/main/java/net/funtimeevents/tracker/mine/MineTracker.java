package net.funtimeevents.tracker.mine;

import net.funtimeevents.model.MinePlayersAroundPayload;
import net.funtimeevents.model.ObservedPlayer;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.tracker.server.ServerContext;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.PlayerNameUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MineTracker implements Tracker {

    private static final double CENTER_X = -75.5;
    private static final double CENTER_Z = 5.5;
    private static final double TRIGGER_RADIUS = 40.0;
    private static final double TRIGGER_RADIUS_SQ = TRIGGER_RADIUS * TRIGGER_RADIUS;
    private static final double SCAN_RADIUS = 15.0;
    private static final double SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;
    private static final String SPAWN_WORLD = "minecraft:lobby";

    private final PayloadSender sender;

    public MineTracker(PayloadSender sender) {
        this.sender = sender;
    }

    @Override
    public void start() {
        FteLogger.info(FteLogger.TRACK, "MineTracker started");
    }

    @Override
    public void stop() {
        FteLogger.info(FteLogger.TRACK, "MineTracker stopped");
    }

    @Override
    public void tick() {
        ServerContext ctx = ServerContext.getInstance();
        if (!ctx.isOnFuntime()) {
            return;
        }

        var client = MinecraftClient.getInstance();
        var world = client.world;
        if (world == null) {
            return;
        }

        String worldId = world.getRegistryKey().getValue().toString();
        if (!SPAWN_WORLD.equals(worldId)) {
            return;
        }

        var localPlayer = client.player;
        if (localPlayer == null) {
            return;
        }
        BlockPos localPos = localPlayer.getBlockPos();
        double ldx = localPos.getX() - CENTER_X;
        double ldz = localPos.getZ() - CENTER_Z;
        if (ldx * ldx + ldz * ldz > TRIGGER_RADIUS_SQ) {
            return;
        }

        List<ObservedPlayer> playersAround = new ArrayList<>();
        String now = Instant.now().toString();

        for (PlayerEntity player : world.getPlayers()) {
            BlockPos pos = player.getBlockPos();
            double dx = pos.getX() - CENTER_X;
            double dz = pos.getZ() - CENTER_Z;
            if (dx * dx + dz * dz > SCAN_RADIUS_SQ) {
                continue;
            }

            String name = PlayerNameUtil.getProfileName(player.getGameProfile());
            String donate = PlayerNameUtil.extractDonate(player);
            playersAround.add(new ObservedPlayer(name, donate, now));
        }

        if (!playersAround.isEmpty()) {
            FteLogger.info(FteLogger.TRACK, "Auto-mine: " + playersAround.size() + " players around spawn");
            var payload = new MinePlayersAroundPayload(ctx.getServerId(), ctx.getServerType(), playersAround);
            sender.sendMinePlayers(payload);
        }
    }
}
