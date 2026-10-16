package net.funtimeevents.tracker.eventcoordinates;

import net.funtimeevents.model.EventCoordinates;
import net.funtimeevents.model.EventCoordinatesPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.poc.slice.ServerContext;
import net.funtimeevents.poc.trackers.TrackLog;
import net.funtimeevents.util.TextUtil;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventCoordinatesTracker implements Tracker {

    private static final Pattern EVENT_PATTERN = Pattern.compile("\\|\\|\\|\\s+\\[(.+?)\\]\\s+\\|\\|\\|");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("Уровень лута:\\s*(.+)");
    private static final Pattern COORDS_PATTERN = Pattern.compile(
            "Появился на координатах\\s+\\[?(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]?");

    private final PayloadSender sender;
    private final ServerContext context;
    private volatile boolean active;

    public EventCoordinatesTracker(PayloadSender sender, ServerContext context) {
        this.sender = sender;
        this.context = context;

    }

    public void handleMessage(net.funtimeevents.poc.slice.IncomingMessage message) {
        if (!active || !context.isOnFuntime() || !message.isGame()) return;
        String quick = message.rawText();
        if (quick != null && !quick.contains("|||")) return;

        String text = message.plainText();
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
                TrackLog.debug(TrackLog.TRACK, "Failed to parse coords: " + coordsMatcher.group());
            }
        }

        ServerContext ctx = context;
        if (x == null || y == null || z == null
                || level == null || level.isBlank()
                || eventName.isBlank()
                || ctx.getServerId() < 0) {
            TrackLog.debug(TrackLog.TRACK, "Event coords skipped (incomplete): event=" + eventName
                    + " level=" + level + " coords=[" + x + ", " + y + ", " + z + "]"
                    + " serverId=" + ctx.getServerId());
            return;
        }

        TrackLog.info(TrackLog.TRACK, "Event coords: " + eventName + " level=" + level + " coords=[" + x + ", " + y + ", " + z + "]");

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
        TrackLog.info(TrackLog.TRACK, "EventCoordinatesTracker started");
    }

    @Override
    public void stop() {
        active = false;
        TrackLog.info(TrackLog.TRACK, "EventCoordinatesTracker stopped");
    }

    @Override
    public void tick() {
    }
}
