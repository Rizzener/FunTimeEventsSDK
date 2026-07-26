package com.funtimeevents.sdk.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerDetector {

    private static final Pattern FUN_TIME_PATTERN = Pattern.compile(
            "^(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*)funtime\\.(su|sh)$"
    );
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("Анархия-(\\d+)");

    private ServerDetector() {
    }

    public static boolean isFuntime(String serverIp) {
        if (serverIp == null) {
            return false;
        }
        return FUN_TIME_PATTERN.matcher(serverIp).matches();
    }

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

    public static String getServerIdHint() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        Scoreboard scoreboard = client.player.getScoreboard();
        if (scoreboard == null) {
            return null;
        }
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return null;
        }
        var displayName = sidebar.getDisplayName();
        return displayName != null ? displayName.getString() : null;
    }
}
