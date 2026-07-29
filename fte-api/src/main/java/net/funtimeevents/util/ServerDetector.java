package net.funtimeevents.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects FunTime servers and extracts metadata from scoreboard / server IP.
 *
 * <p>Used internally by trackers to determine whether the player is
 * on a supported server and to read the current server ID.
 */
public final class ServerDetector {

    private static final Pattern FUN_TIME_PATTERN = Pattern.compile(
            "^(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*)funtime\\.(su|sh|me|store|network|wiki)$"
    );
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("Анархия-(\\d+)");

    private ServerDetector() {
    }

    /** Returns {@code true} if the given server address belongs to a FunTime network server. */
    public static boolean isFuntime(String serverIp) {
        if (serverIp == null) {
            return false;
        }
        return FUN_TIME_PATTERN.matcher(serverIp).matches();
    }

    /** Extracts the server ID from scoreboard title text (e.g. {@code Анархия-4 → 4}). */
    public static int extractServerId(String text) {
        if (text == null) {
            return -1;
        }
        Matcher m = SERVER_ID_PATTERN.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    public static String getCurrentServerIp() {
        var client = MinecraftClient.getInstance();
        var server = client.getCurrentServerEntry();
        if (server != null) {
            return server.address;
        }
        return null;
    }

    public static String getSidebarTitle() {
        var client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        var scoreboard = client.world.getScoreboard();
        if (scoreboard == null) {
            return null;
        }
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return null;
        }
        var displayName = sidebar.getDisplayName();
        if (displayName == null) return null;
        String raw = TextUtil.tryGetRawText(displayName);
        return raw != null ? raw : displayName.getString();
    }
}
