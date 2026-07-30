package net.funtimeevents.tracker.server;

import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.util.FteLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerContext implements Tracker {

    private static final ServerContext INSTANCE = new ServerContext();

    private volatile int serverId = -1;
    private volatile String serverIp;
    private volatile boolean onFuntime;
    private final String serverType = "anarchy";

    private Object cachedTitleText;
    private String cachedTitleString;

    private ServerContext() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> refresh());
    }

    public static ServerContext getInstance() {
        return INSTANCE;
    }

    public int getServerId() {
        if (!onFuntime) {
            throw new IllegalStateException(
                    "server_id requested but not on FunTime. Check isOnFuntime() first.");
        }
        return serverId;
    }

    public String getServerType() {
        return serverType;
    }

    public boolean isOnFuntime() {
        return onFuntime;
    }

    @Override
    public void start() {
        refresh();
        FteLogger.info(FteLogger.TRACK, "ServerContext started, serverId=" + serverId + " onFuntime=" + onFuntime);
    }

    @Override
    public void stop() {
        FteLogger.info(FteLogger.TRACK, "ServerContext stopped");
        serverId = -1;
        serverIp = null;
        onFuntime = false;
        cachedTitleText = null;
        cachedTitleString = null;
    }

    @Override
    public void tick() {
        refresh();
    }

    private void refresh() {
        boolean wasFuntime = onFuntime;
        int oldId = serverId;

        serverIp = getCurrentServerIp();
        onFuntime = isFuntime(serverIp);

        if (onFuntime) {
            String hint = getSidebarText();
            int newServerId = extractServerId(hint);

            if (!wasFuntime) {
                serverId = newServerId;
                FteLogger.info(FteLogger.TRACK, "ServerContext: joined FunTime server, ip=" + serverIp + " serverId=" + serverId);
            } else if (newServerId != serverId) {
                serverId = newServerId;
                FteLogger.info(FteLogger.TRACK, "ServerContext: serverId changed " + oldId + " -> " + serverId + " hint=" + hint);
            }
        } else {
            if (wasFuntime) {
                FteLogger.info(FteLogger.TRACK, "ServerContext: left FunTime server, ip=" + serverIp);
            }
            serverId = -1;
        }
    }

    private String getSidebarText() {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        var scoreboard = client.world.getScoreboard();
        if (scoreboard == null) return null;
        var sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) return null;
        var displayName = sidebar.getDisplayName();
        if (displayName == null) return null;
        if (displayName == cachedTitleText) {
            return cachedTitleString;
        }
        cachedTitleText = displayName;
        cachedTitleString = displayName.getString();
        return cachedTitleString;
    }

    private static final Pattern FUN_TIME_PATTERN = Pattern.compile(
            "^(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*)funtime\\.(su|sh|me|store|network|wiki)$"
    );
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("Анархия-(\\d+)");

    private static String getCurrentServerIp() {
        var client = MinecraftClient.getInstance();
        var server = client.getCurrentServerEntry();
        return server != null ? server.address : null;
    }

    private static boolean isFuntime(String serverIp) {
        return serverIp != null && FUN_TIME_PATTERN.matcher(serverIp).matches();
    }

    private static int extractServerId(String text) {
        if (text == null) return -1;
        Matcher m = SERVER_ID_PATTERN.matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }
}
