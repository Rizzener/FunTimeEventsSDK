package com.funtimeevents.sdk.tracker.hell;

import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HellMapTracker implements Tracker {

    private static final Pattern HELL_PATTERN = Pattern.compile("Адская резня - Осталось мобов:\\s*(\\d+)");
    private static final int TICK_INTERVAL = 100; // 5 seconds

    private static volatile Field bossBarsField;
    private static volatile Method getNameMethod;

    private final PayloadSender sender;
    private volatile boolean active;
    private int tickCounter;

    public HellMapTracker(PayloadSender sender) {
        this.sender = sender;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active || TabHeaderTracker.getInstance() == null) return;
            tickCounter++;
            if (tickCounter >= TICK_INTERVAL) {
                tickCounter = 0;
                doScan();
            }
        });
    }

    private static Field resolveBossBarsField() {
        if (bossBarsField != null) return bossBarsField;
        try {
            var bossBarHud = MinecraftClient.getInstance().inGameHud.getBossBarHud();
            var field = bossBarHud.getClass().getDeclaredField("bossBars");
            field.setAccessible(true);
            bossBarsField = field;
        } catch (Exception e) {
            FteLogger.error(FteLogger.TRACK, "bossBars field not found: " + e.getMessage());
        }
        return bossBarsField;
    }

    private static Method resolveGetNameMethod(Object bar) {
        if (getNameMethod != null) return getNameMethod;
        try {
            getNameMethod = bar.getClass().getMethod("getName");
        } catch (Exception e) {
            FteLogger.error(FteLogger.TRACK, "getName method not found: " + e.getMessage());
        }
        return getNameMethod;
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
        // scanning is triggered by ClientTickEvents at 5s intervals
    }

    private void doScan() {
        TabHeaderTracker header = TabHeaderTracker.getInstance();
        if (!header.isOnFuntime()) {
            return;
        }

        var client = MinecraftClient.getInstance();
        var bossBarHud = client.inGameHud.getBossBarHud();
        Field field = resolveBossBarsField();
        if (field == null) return;

        Map<?, ?> bars;
        try {
            bars = (Map<?, ?>) field.get(bossBarHud);
        } catch (Exception e) {
            return;
        }

        if (bars.isEmpty()) {
            return;
        }

        for (var entry : bars.entrySet()) {
            var bar = entry.getValue();
            Method nameMethod = resolveGetNameMethod(bar);
            if (nameMethod == null) continue;
            Text name;
            try {
                name = (Text) nameMethod.invoke(bar);
            } catch (Exception e) {
                continue;
            }
            String text = name.getString();
            Matcher m = HELL_PATTERN.matcher(text);
            if (!m.find()) {
                continue;
            }
            int mobsCount = Integer.parseInt(m.group(1));
            FteLogger.info(FteLogger.TRACK, "HellMap: mobs_count=" + mobsCount + " text=" + text);

            HellMapPayload payload = new HellMapPayload(header.getServerId(), header.getServerType(), mobsCount);
            sender.sendHellMap(payload);
            return;
        }
    }
}
