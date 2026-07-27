package com.funtimeevents.sdk.tracker.mine;

import com.funtimeevents.sdk.model.MinePlayersAroundPayload;
import com.funtimeevents.sdk.model.TabPlayer;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import com.funtimeevents.sdk.util.PlayerNameUtil;
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
    }

    @Override
    public void stop() {
    }

    @Override
    public void tick() {
        TabHeaderTracker header = TabHeaderTracker.getInstance();
        if (!header.isOnFuntime()) {
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

        List<TabPlayer> playersAround = new ArrayList<>();
        String now = Instant.now().toString();

        for (PlayerEntity player : world.getPlayers()) {
            BlockPos pos = player.getBlockPos();
            double dx = pos.getX() - CENTER_X;
            double dz = pos.getZ() - CENTER_Z;
            if (dx * dx + dz * dz > SCAN_RADIUS_SQ) {
                continue;
            }

            String name = player.getGameProfile().getName();
            String donate = PlayerNameUtil.extractDonate(player);
            playersAround.add(new TabPlayer(name, donate, now));
        }

        if (!playersAround.isEmpty()) {
            FteLogger.info("Mine lobby: " + playersAround.size() + " players around spawn");
            var payload = new MinePlayersAroundPayload(header.getServerId(), header.getServerType(), playersAround);
            sender.sendMinePlayers(payload);
        }
    }
}
