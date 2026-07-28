package net.funtimeevents.tracker.server;

import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.ServerDetector;

/**
 * Singleton that detects which FunTime server the player is on.
 *
 * <p>On every tick it reads the current server IP and the scoreboard
 * sidebar title to extract the server ID (e.g. {@code Анархия-4 → 4}).
 * All trackers call {@link #getInstance()} to access this context.
 */
public final class ServerContext implements Tracker {

    private static final ServerContext INSTANCE = new ServerContext();

    private volatile int serverId = -1;
    private volatile String serverIp;
    private volatile boolean onFuntime;
    private final String serverType = "anarchy";

    private ServerContext() {
    }

    public static ServerContext getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the current FunTime server ID.
     *
     * @throws IllegalStateException if the player is not on a FunTime server.
     *         Always call {@link #isOnFuntime()} first.
     */
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
    }

    @Override
    public void tick() {
        refresh();
    }

    private void refresh() {
        serverIp = ServerDetector.getCurrentServerIp();
        onFuntime = ServerDetector.isFuntime(serverIp);

        if (onFuntime) {
            String hint = ServerDetector.getSidebarTitle();
            int newServerId = ServerDetector.extractServerId(hint);
            if (newServerId != serverId) {
                serverId = newServerId;
            }
        } else {
            serverId = -1;
        }
    }
}
