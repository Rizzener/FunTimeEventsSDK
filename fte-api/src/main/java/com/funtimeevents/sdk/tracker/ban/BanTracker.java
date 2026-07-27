package com.funtimeevents.sdk.tracker.ban;

import com.funtimeevents.sdk.event.BanDetectedEvent;
import com.funtimeevents.sdk.event.EventBus;
import com.funtimeevents.sdk.model.BanPayload;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BanTracker implements Tracker {

    private static final Pattern BAN_PATTERN = Pattern.compile("\\[♨\\] (.+?) забанен \\[Подробнее\\]");

    private static final Pattern HOVER_REASON_PATTERN = Pattern.compile("Причина:\\s*(.+)");
    private static final Pattern HOVER_END_PATTERN = Pattern.compile("(?:До|Наказание|Бан до|Окончание):\\s*(.+)");
    private static final Pattern HOVER_SERVER_PATTERN = Pattern.compile("Сервер:\\s*(.+)");

    private final PayloadSender sender;
    private boolean active;

    public BanTracker(PayloadSender sender) {
        this.sender = sender;
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, s, params, receptionTimestamp) -> {
            if (!active || !TabHeaderTracker.getInstance().isOnFuntime()) {
                return;
            }
            handleMessage(message);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!active || !TabHeaderTracker.getInstance().isOnFuntime()) {
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
        FteLogger.info("Ban detected: player=" + playerName + ", hover=" + hoverText);

        EventBus.getInstance().publish(BanDetectedEvent.create(playerName, hoverText));

        String reason = parseReason(hoverText);
        String end = parseEnd(hoverText);

        TabHeaderTracker header = TabHeaderTracker.getInstance();
        int serverId = parseServerId(hoverText);
        if (serverId <= 0) {
            serverId = header.getServerId();
        }
        String serverType = header.getServerType();

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
                Object action = hoverEvent.getClass().getMethod("getAction").invoke(hoverEvent);
                if (action != null && action.toString().contains("show_text")) {
                    Object value = hoverEvent.getClass().getMethod("getValue", action.getClass()).invoke(hoverEvent, action);
                    if (value instanceof Text hoverText) {
                        return Optional.of(hoverText.getString());
                    }
                }
            } catch (Exception e) {
                FteLogger.debug("Hover text extraction failed: " + e.getMessage());
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
        FteLogger.info("BanTracker started, onFuntime=" + TabHeaderTracker.getInstance().isOnFuntime());
    }

    @Override
    public void stop() {
        active = false;
    }

    @Override
    public void tick() {
    }
}
