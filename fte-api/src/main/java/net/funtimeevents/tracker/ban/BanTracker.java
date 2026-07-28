package net.funtimeevents.tracker.ban;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.tracker.server.ServerContext;
import net.funtimeevents.util.FteLogger;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BanTracker implements Tracker {

    private static final Pattern BAN_PATTERN = Pattern.compile("\\[♨\\] (.+?) забанен \\[Подробнее\\]");

    private static final Pattern HOVER_REASON_PATTERN = Pattern.compile("Причина:\\s*(.+)");
    private static final Pattern HOVER_END_PATTERN = Pattern.compile("(?:До|Наказание|Бан до|Окончание):\\s*(.+)");
    private static final Pattern HOVER_SERVER_PATTERN = Pattern.compile("Сервер:\\s*(.+)");

    private static volatile Method GET_ACTION_METHOD;
    private static volatile Method GET_VALUE_METHOD;

    private final PayloadSender sender;
    private volatile boolean active;

    public BanTracker(PayloadSender sender) {
        this.sender = sender;
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, s, params, receptionTimestamp) -> {
            if (!active || !ServerContext.getInstance().isOnFuntime()) {
                return;
            }
            handleMessage(message);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!active || !ServerContext.getInstance().isOnFuntime()) {
                return;
            }
            handleMessage(message);
        });
    }

    private void handleMessage(Text message) {
        String plainText = message.getString();
        Matcher matcher = BAN_PATTERN.matcher(plainText);
        if (!matcher.find()) {
            return;
        }
        String playerName = matcher.group(1);
        String hoverText = extractHoverText(message);
        FteLogger.info(FteLogger.TRACK, "Ban detected: player=" + playerName + ", hover=" + hoverText);

        String reason = parseReason(hoverText);
        String end = parseEnd(hoverText);

        ServerContext ctx = ServerContext.getInstance();
        int serverId = parseServerId(hoverText);
        if (serverId <= 0) {
            serverId = ctx.getServerId();
        }
        String serverType = ctx.getServerType();

        BanPayload payload = new BanPayload(serverId, serverType, playerName, reason, end);
        sender.sendBan(payload);
    }

    private String extractHoverText(Text message) {
        return findHoverText(message).orElse("");
    }

    private Optional<String> findHoverText(Text component) {
        var hoverEvent = component.getStyle().getHoverEvent();
        if (hoverEvent != null) {
            try {
                Method getAction = resolveGetAction(hoverEvent);
                if (getAction == null) return Optional.empty();
                Object action = getAction.invoke(hoverEvent);
                if (action != null && action.toString().contains("show_text")) {
                    Method getValue = resolveGetValue(hoverEvent, action.getClass());
                    if (getValue == null) return Optional.empty();
                    Object value = getValue.invoke(hoverEvent, action);
                    if (value instanceof Text hoverText) {
                        return Optional.of(hoverText.getString());
                    }
                }
            } catch (Exception e) {
                FteLogger.debug(FteLogger.TRACK, "Hover text extraction failed: " + e.getMessage());
            }
        }
        for (Text sibling : component.getSiblings()) {
            var result = findHoverText(sibling);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private static Method resolveGetAction(Object hoverEvent) {
        if (GET_ACTION_METHOD != null) return GET_ACTION_METHOD;
        try {
            GET_ACTION_METHOD = hoverEvent.getClass().getMethod("getAction");
        } catch (Exception e) {
            FteLogger.debug(FteLogger.TRACK, "getAction method not found: " + e.getMessage());
        }
        return GET_ACTION_METHOD;
    }

    private static Method resolveGetValue(Object hoverEvent, Class<?> actionClass) {
        if (GET_VALUE_METHOD != null) return GET_VALUE_METHOD;
        try {
            GET_VALUE_METHOD = hoverEvent.getClass().getMethod("getValue", actionClass);
        } catch (Exception e) {
            FteLogger.debug(FteLogger.TRACK, "getValue method not found: " + e.getMessage());
        }
        return GET_VALUE_METHOD;
    }

    static String parseReason(String hoverText) {
        Matcher m = HOVER_REASON_PATTERN.matcher(hoverText);
        return m.find() ? m.group(1).trim() : "";
    }

    static String parseEnd(String hoverText) {
        Matcher m = HOVER_END_PATTERN.matcher(hoverText);
        return m.find() ? m.group(1).trim() : "Навсегда";
    }

    static int parseServerId(String hoverText) {
        Matcher m = HOVER_SERVER_PATTERN.matcher(hoverText);
        if (m.find()) {
            String server = m.group(1).trim();
            String digits = server.replaceAll("\\D+", "");
            if (!digits.isEmpty()) {
                return Integer.parseInt(digits);
            }
        }
        return -1;
    }

    @Override
    public void start() {
        active = true;
        FteLogger.info(FteLogger.TRACK, "BanTracker started, onFuntime=" + ServerContext.getInstance().isOnFuntime());
    }

    @Override
    public void stop() {
        active = false;
        FteLogger.info(FteLogger.TRACK, "BanTracker stopped");
    }

    @Override
    public void tick() {
    }
}
