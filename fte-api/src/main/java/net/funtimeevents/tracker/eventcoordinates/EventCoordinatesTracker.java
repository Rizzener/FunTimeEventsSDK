package net.funtimeevents.tracker.eventcoordinates;

import net.funtimeevents.model.EventCoordinates;
import net.funtimeevents.model.EventCoordinatesPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.tracker.server.ServerContext;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.TextUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

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
            if (!active || !ServerContext.getInstance().isOnFuntime()) {
                return;
            }
            handleMessage(message);
        });
    }

    private void handleMessage(Text message) {
        String quick = TextUtil.tryGetRawText(message);
        if (quick != null && !quick.contains("|||")) return;

        String text = message.getString();
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
                FteLogger.debug(FteLogger.TRACK, "Failed to parse coords: " + coordsMatcher.group());
            }
        }

        ServerContext ctx = ServerContext.getInstance();
        if (x == null || y == null || z == null
                || level == null || level.isBlank()
                || eventName.isBlank()
                || ctx.getServerId() < 0) {
            FteLogger.debug(FteLogger.TRACK, "Event coords skipped (incomplete): event=" + eventName
                    + " level=" + level + " coords=[" + x + ", " + y + ", " + z + "]"
                    + " serverId=" + ctx.getServerId());
            return;
        }

        FteLogger.info(FteLogger.TRACK, "Event coords: " + eventName + " level=" + level + " coords=[" + x + ", " + y + ", " + z + "]");

        EventCoordinatesPayload payload = new EventCoordinatesPayload(
                ctx.getServerId(), ctx.getServerType(),
                eventName, level,
                new EventCoordinates(x, y, z)
        );
        sender.sendEventCoordinates(payload);
    }

    @Override
    public void start() {
        active = true;
        FteLogger.info(FteLogger.TRACK, "EventCoordinatesTracker started");
    }

    @Override
    public void stop() {
        active = false;
        FteLogger.info(FteLogger.TRACK, "EventCoordinatesTracker stopped");
    }

    @Override
    public void tick() {
    }
}
