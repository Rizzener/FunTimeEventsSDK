package net.funtimeevents.poc.slice;

import net.funtimeevents.model.BanPayload;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Original BanTracker parsing rules; platform subscriptions are outside core. */
public final class BanTracker {
    private static final Pattern BAN_PATTERN = Pattern.compile("\\[♨\\] (.+?) забанен \\[Подробнее\\]");
    private static final Pattern HOVER_REASON_PATTERN = Pattern.compile("Причина:\\s*(.+)");
    private static final Pattern HOVER_END_PATTERN = Pattern.compile("(?:До|Наказание|Бан до|Окончание):\\s*(.+)");
    private static final Pattern HOVER_SERVER_PATTERN = Pattern.compile("Сервер:\\s*(.+)");

    private final Consumer<BanPayload> sender;
    private final ServerContext context;
    private volatile boolean active;

    public BanTracker(Consumer<BanPayload> sender, ServerContext context) {
        this.sender = java.util.Objects.requireNonNull(sender);
        this.context = context;
    }

    public void start() { active = true; }
    public void stop() { active = false; }

    public void handleMessage(IncomingMessage message) {
        if (!active || !context.isOnFuntime()) return;
        String quick = message.rawText();
        if (quick != null && !quick.contains("[♨]")) return;
        String plainText = message.plainText();
        Matcher matcher = BAN_PATTERN.matcher(plainText);
        if (!matcher.find()) return;
        String playerName = matcher.group(1);
        String hoverText = message.hoverText();
        String reason = parseReason(hoverText);
        String end = parseEnd(hoverText);
        int serverId = parseServerId(hoverText);
        if (serverId <= 0) serverId = context.getServerId();
        if (serverId < 0) return;
        String serverType = context.getServerType();
        sender.accept(new BanPayload(serverId, serverType, playerName, reason, end));
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
}
