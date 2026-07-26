package com.funtimeevents.sdk.tracker.spawn;

import com.funtimeevents.sdk.api.FunTimeEventsAPI;
import com.funtimeevents.sdk.model.SpawnCoordinates;
import com.funtimeevents.sdk.model.SpawnEventPayload;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpawnEventTracker implements Tracker {

    private static final Pattern EVENT_PATTERN = Pattern.compile("\\|\\|\\|\\s+\\[(.+?)\\]\\s+\\|\\|\\|");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("Уровень лута:\\s*(.+)");
    private static final Pattern COORDS_PATTERN = Pattern.compile("Появился на координатах\\s+\\[?([\\-\\d]+)\\s+([\\-\\d]+)\\s+([\\-\\d]+)\\]?");

    private boolean active;

    public SpawnEventTracker() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!active || !TabHeaderTracker.getInstance().isOnFuntime()) {
                return;
            }
            handleMessage(message.getString());
        });
    }

    private void handleMessage(String text) {
        Matcher eventMatcher = EVENT_PATTERN.matcher(text);
        if (!eventMatcher.find()) {
            return;
        }
        String eventName = eventMatcher.group(1);

        Matcher levelMatcher = LEVEL_PATTERN.matcher(text);
        String level = levelMatcher.find() ? levelMatcher.group(1).trim() : null;

        Matcher coordsMatcher = COORDS_PATTERN.matcher(text);
        Integer x = null, y = null, z = null;
        if (coordsMatcher.find()) {
            x = Integer.parseInt(coordsMatcher.group(1));
            y = Integer.parseInt(coordsMatcher.group(2));
            z = Integer.parseInt(coordsMatcher.group(3));
        }

        FteLogger.info("Spawn event: " + eventName + " level=" + level + " coords=[" + x + ", " + y + ", " + z + "]");

        TabHeaderTracker header = TabHeaderTracker.getInstance();
        SpawnEventPayload payload = new SpawnEventPayload(
                header.getServerId(), header.getServerType(),
                eventName, level,
                new SpawnCoordinates(x, y, z)
        );
        FunTimeEventsAPI.sendSpawnEvent(payload);
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
    }
}
