package com.funtimeevents.sdk.tracker.hell;

import com.funtimeevents.sdk.api.FunTimeEventsAPI;
import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HellMapTracker implements Tracker {

    private static final Pattern HELL_PATTERN = Pattern.compile("Адская резня - Осталось мобов:\\s*(\\d+)");

    private boolean active;

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
        Map<?, ?> bars;
        try {
            var field = bossBarHud.getClass().getDeclaredField("bossBars");
            field.setAccessible(true);
            bars = (Map<?, ?>) field.get(bossBarHud);
        } catch (Exception e) {
            return;
        }

        if (bars.isEmpty()) {
            return;
        }

        for (var entry : bars.entrySet()) {
            var bar = entry.getValue();
            Text name;
            try {
                name = (Text) bar.getClass().getMethod("getName").invoke(bar);
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
            FunTimeEventsAPI.sendHellMap(payload);
            return;
        }
    }
}
