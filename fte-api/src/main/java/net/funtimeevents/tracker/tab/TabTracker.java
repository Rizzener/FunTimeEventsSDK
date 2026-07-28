package net.funtimeevents.tracker.tab;

import net.funtimeevents.model.TabPlayer;
import net.funtimeevents.tracker.Tracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.PlayerNameUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TabTracker implements Tracker {

    @Override
    public void start() {
        FteLogger.info(FteLogger.TRACK, "TabTracker started");
    }

    @Override
    public void stop() {
        FteLogger.info(FteLogger.TRACK, "TabTracker stopped");
    }

    @Override
    public void tick() {
    }

    public List<TabPlayer> getCurrentPlayers() {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            return Collections.emptyList();
        }
        String now = Instant.now().toString();
        List<TabPlayer> result = new ArrayList<>();
        for (PlayerListEntry entry : handler.getPlayerList()) {
            String name = entry.getProfile().getName();
            String donate = PlayerNameUtil.extractDonate(entry);
            result.add(new TabPlayer(name, donate, now));
        }
        return result;
    }
}
