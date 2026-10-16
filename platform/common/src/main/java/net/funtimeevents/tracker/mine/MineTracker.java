package net.funtimeevents.tracker.mine;

import net.funtimeevents.model.MinePlayersAroundPayload;
import net.funtimeevents.model.ObservedPlayer;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.internal.game.ServerContext;
import net.funtimeevents.internal.tracker.TrackLog;
import net.funtimeevents.util.PlayerNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;

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
    private final ServerContext context;

    public MineTracker(PayloadSender sender, ServerContext context) {
        this.sender = sender;
        this.context = context;
    }

    @Override
    public void start() {
        TrackLog.info(TrackLog.TRACK, "MineTracker started");
    }

    @Override
    public void stop() {
        TrackLog.info(TrackLog.TRACK, "MineTracker stopped");
    }

    @Override
    public void tick() {
        ServerContext ctx = context;
        if (!ctx.isOnFuntime()) {
            return;
        }
        if (ctx.getServerId() < 0) {
            TrackLog.warn(TrackLog.TRACK, "MineTracker skipped: serverId not resolved");
            return;
        }

        var client = Minecraft.getInstance();
        var world = client.level;
        if (world == null) {
            return;
        }

        String worldId = net.funtimeevents.internal.platform.VersionAccess.dimension(world);
        if (!SPAWN_WORLD.equals(worldId)) {
            return;
        }

        var localPlayer = client.player;
        if (localPlayer == null) {
            return;
        }
        BlockPos localPos = localPlayer.blockPosition();
        double ldx = localPos.getX() - CENTER_X;
        double ldz = localPos.getZ() - CENTER_Z;
        if (ldx * ldx + ldz * ldz > TRIGGER_RADIUS_SQ) {
            return;
        }

        List<ObservedPlayer> playersAround = new ArrayList<>();
        String now = Instant.now().toString();

        for (Player player : world.players()) {
            BlockPos pos = player.blockPosition();
            double dx = pos.getX() - CENTER_X;
            double dz = pos.getZ() - CENTER_Z;
            if (dx * dx + dz * dz > SCAN_RADIUS_SQ) {
                continue;
            }

            var info = PlayerNameUtil.resolvePlayer(player);
            if (info == null) continue;
            playersAround.add(new ObservedPlayer(info.name(), info.donate(), now));
        }

        if (!playersAround.isEmpty()) {
            TrackLog.info(TrackLog.TRACK, "Auto-mine: " + playersAround.size() + " players around spawn");
            var payload = new MinePlayersAroundPayload(ctx.getServerId(), ctx.getServerType(), playersAround);
            sender.sendMinePlayers(payload);
        }
    }
}
