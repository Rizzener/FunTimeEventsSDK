package com.funtimeevents.sdk.tracker.eventcoordinates;

import com.funtimeevents.sdk.model.EventCoordinates;
import com.funtimeevents.sdk.model.EventCoordinatesPayload;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventCoordinatesTracker implements Tracker {

    private static final Pattern EVENT_PATTERN = Pattern.compile("\\|\\|\\|\\s+\\[(.+?)\\]\\s+\\|\\|\\|");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("Уровень лута:\\s*(.+)");
    private static final Pattern COORDS_PATTERN = Pattern.compile(
            "Появился на координатах\\s+\\[?(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]?");

    private final PayloadSender sender;
    private volatile boolean active;

    public EventCoordinatesTracker(PayloadSender sender) {
        this.sender = sender;
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
            try {
                x = Integer.parseInt(coordsMatcher.group(1));
                y = Integer.parseInt(coordsMatcher.group(2));
                z = Integer.parseInt(coordsMatcher.group(3));
            } catch (NumberFormatException e) {
                FteLogger.debug("Failed to parse coords: " + coordsMatcher.group());
            }
        }

        FteLogger.info("Event coords: " + eventName + " level=" + level + " coords=[" + x + ", " + y + ", " + z + "]");

        TabHeaderTracker header = TabHeaderTracker.getInstance();
        EventCoordinatesPayload payload = new EventCoordinatesPayload(
                header.getServerId(), header.getServerType(),
                eventName, level,
                new EventCoordinates(x, y, z)
        );
        sender.sendEventCoordinates(payload);
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
