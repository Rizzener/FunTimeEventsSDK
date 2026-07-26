package com.funtimeevents.sdk.tracker.tab;

import com.funtimeevents.sdk.model.TabPlayer;
import com.funtimeevents.sdk.tracker.Tracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TabTracker implements Tracker {

    @Override
    public void start() {
    }

    @Override
    public void stop() {
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
            String donate = extractDonate(entry);
            result.add(new TabPlayer(name, donate, now));
        }
        return result;
    }

    private String extractDonate(PlayerListEntry entry) {
        if (entry.getDisplayName() != null) {
            String displayName = entry.getDisplayName().getString();
            String profileName = entry.getProfile().getName();
            if (displayName.length() > profileName.length() && displayName.contains(profileName)) {
                return displayName.substring(0, displayName.indexOf(profileName)).trim();
            }
            return displayName;
        }
        return "";
    }
}
