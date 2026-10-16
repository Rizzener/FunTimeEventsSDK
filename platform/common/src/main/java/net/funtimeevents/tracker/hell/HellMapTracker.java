package net.funtimeevents.tracker.hell;

import net.funtimeevents.model.HellMapPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.internal.game.ServerContext;
import net.funtimeevents.util.BossBarUtil;
import net.funtimeevents.internal.tracker.TrackLog;
import net.funtimeevents.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HellMapTracker implements Tracker {

    private static final Pattern HELL_PATTERN = Pattern.compile("Адская резня - Осталось мобов:\\s*(\\d+)");
    private static final int TICK_INTERVAL = 100; // 5 seconds

    private final PayloadSender sender;
    private final ServerContext context;
    private volatile boolean active;
    private int tickCounter;

    public HellMapTracker(PayloadSender sender, ServerContext context) {
        this.sender = sender;
        this.context = context;

    }


    /** Called by the one platform listener, with the original 100-tick cadence. */
    public void clientTick() {
        if (!active || !context.isOnFuntime()) return;
        tickCounter++;
        if (tickCounter >= TICK_INTERVAL) {
            tickCounter = 0;
            long started = net.funtimeevents.internal.tracker.TrackerMetrics.begin();
            doScan();
            net.funtimeevents.internal.tracker.TrackerMetrics.end("hell", started);
        }
    }

    @Override
    public void start() {
        active = true;
        tickCounter = 0;
        TrackLog.info(TrackLog.TRACK, "HellMapTracker started");
    }

    @Override
    public void stop() {
        active = false;
        TrackLog.info(TrackLog.TRACK, "HellMapTracker stopped");
    }

    @Override
    public void tick() {
    }

    private void doScan() {
        var client = Minecraft.getInstance();
        var world = client.level;
        if (world == null) return;

        String dim = net.funtimeevents.internal.platform.VersionAccess.dimension(world);
        if (!"minecraft:nether-event".equals(dim)) return;

        ServerContext ctx = context;
        if (!ctx.isOnFuntime()) {
            return;
        }
        if (ctx.getServerId() < 0) {
            TrackLog.warn(TrackLog.TRACK, "HellMapTracker skipped: serverId not resolved");
            return;
        }

        Map<?, ?> bars = BossBarUtil.getBossBars();
        if (bars == null || bars.isEmpty()) {
            return;
        }

        for (var entry : bars.entrySet()) {
            var bar = entry.getValue();
            Component name = BossBarUtil.getBossBarName(bar);
            if (name == null) continue;
            String raw = TextUtil.tryGetRawText(name);
            String text = raw != null ? raw : name.getString();
            Matcher m = HELL_PATTERN.matcher(text);
            if (!m.find()) {
                continue;
            }
            int mobsCount = Integer.parseInt(m.group(1));
            TrackLog.info(TrackLog.TRACK, "HellMap: mobs_count=" + mobsCount + " text=" + text);

            HellMapPayload payload = new HellMapPayload(ctx.getServerId(), ctx.getServerType(), mobsCount);
            sender.sendHellMap(payload);
            return;
        }
    }
}
