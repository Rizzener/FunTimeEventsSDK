package net.funtimeevents.tracker.hell;

import net.funtimeevents.model.HellMapPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.tracker.server.ServerContext;
import net.funtimeevents.util.BossBarUtil;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.TextUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HellMapTracker implements Tracker {

    private static final Pattern HELL_PATTERN = Pattern.compile("Адская резня - Осталось мобов:\\s*(\\d+)");
    private static final int TICK_INTERVAL = 100; // 5 seconds

    private final PayloadSender sender;
    private volatile boolean active;
    private int tickCounter;

    public HellMapTracker(PayloadSender sender) {
        this.sender = sender;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active || !ServerContext.getInstance().isOnFuntime()) return;
            tickCounter++;
            if (tickCounter >= TICK_INTERVAL) {
                tickCounter = 0;
                doScan();
            }
        });
    }

    @Override
    public void start() {
        active = true;
        tickCounter = 0;
        FteLogger.info(FteLogger.TRACK, "HellMapTracker started");
    }

    @Override
    public void stop() {
        active = false;
        FteLogger.info(FteLogger.TRACK, "HellMapTracker stopped");
    }

    @Override
    public void tick() {
    }

    private void doScan() {
        var client = MinecraftClient.getInstance();
        var world = client.world;
        if (world == null) return;

        String dim = world.getRegistryKey().getValue().toString();
        if (!"minecraft:nether-event".equals(dim)) return;

        ServerContext ctx = ServerContext.getInstance();
        if (!ctx.isOnFuntime()) {
            return;
        }

        Map<?, ?> bars = BossBarUtil.getBossBars();
        if (bars == null || bars.isEmpty()) {
            return;
        }

        for (var entry : bars.entrySet()) {
            var bar = entry.getValue();
            Text name = BossBarUtil.getBossBarName(bar);
            if (name == null) continue;
            String raw = TextUtil.tryGetRawText(name);
            String text = raw != null ? raw : name.getString();
            Matcher m = HELL_PATTERN.matcher(text);
            if (!m.find()) {
                continue;
            }
            int mobsCount = Integer.parseInt(m.group(1));
            FteLogger.info(FteLogger.TRACK, "HellMap: mobs_count=" + mobsCount + " text=" + text);

            HellMapPayload payload = new HellMapPayload(ctx.getServerId(), ctx.getServerType(), mobsCount);
            sender.sendHellMap(payload);
            return;
        }
    }
}
