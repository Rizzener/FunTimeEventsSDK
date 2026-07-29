package net.funtimeevents.tracker.ban;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.tracker.server.ServerContext;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.HoverEventUtil;
import net.funtimeevents.util.TextUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BanTracker implements Tracker {

    private static final Pattern BAN_PATTERN = Pattern.compile("\\[♨\\] (.+?) забанен \\[Подробнее\\]");

    private static final Pattern HOVER_REASON_PATTERN = Pattern.compile("Причина:\\s*(.+)");
    private static final Pattern HOVER_END_PATTERN = Pattern.compile("(?:До|Наказание|Бан до|Окончание):\\s*(.+)");
    private static final Pattern HOVER_SERVER_PATTERN = Pattern.compile("Сервер:\\s*(.+)");

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
        String quick = TextUtil.tryGetRawText(message);
        if (quick != null && !quick.contains("[♨]")) return;

        String plainText = message.getString();
        Matcher matcher = BAN_PATTERN.matcher(plainText);
        if (!matcher.find()) {
            return;
        }
        String playerName = matcher.group(1);
        String hoverText = HoverEventUtil.extractHoverText(message);
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
