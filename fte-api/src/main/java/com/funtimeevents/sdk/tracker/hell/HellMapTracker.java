package com.funtimeevents.sdk.tracker.hell;

import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HellMapTracker implements Tracker {

    private static final Pattern HELL_PATTERN = Pattern.compile("Адская резня - Осталось мобов:\\s*(\\d+)");

    private static volatile Field bossBarsField;
    private static volatile Method getNameMethod;

    private final PayloadSender sender;
    private boolean active;

    public HellMapTracker(PayloadSender sender) {
        this.sender = sender;
    }

    private static Field resolveBossBarsField() {
        if (bossBarsField != null) return bossBarsField;
        try {
            var bossBarHud = MinecraftClient.getInstance().inGameHud.getBossBarHud();
            var field = bossBarHud.getClass().getDeclaredField("bossBars");
            field.setAccessible(true);
            bossBarsField = field;
        } catch (Exception e) {
            FteLogger.error("bossBars field not found: " + e.getMessage());
        }
        return bossBarsField;
    }

    private static Method resolveGetNameMethod(Object bar) {
        if (getNameMethod != null) return getNameMethod;
        try {
            getNameMethod = bar.getClass().getMethod("getName");
        } catch (Exception e) {
            FteLogger.error("getName method not found: " + e.getMessage());
        }
        return getNameMethod;
    }

    @Override
    public void start() {
        active = true;
    }

    @Override
    public void stop() {
        active = false;
    }

    @Override
    public void tick() {
        if (!active) {
            return;
        }
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
            FteLogger.info("HellMap: mobs_count=" + mobsCount + " text=" + text);

            HellMapPayload payload = new HellMapPayload(header.getServerId(), header.getServerType(), mobsCount);
            sender.sendHellMap(payload);
            return;
        }
    }
}
