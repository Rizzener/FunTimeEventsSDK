package com.funtimeevents.sdk.tracker.tabheader;

import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.util.FteLogger;
import com.funtimeevents.sdk.util.ServerDetector;

public final class TabHeaderTracker implements Tracker {

    private static final TabHeaderTracker INSTANCE = new TabHeaderTracker();

    private volatile int serverId = -1;
    private volatile String serverIp;
    private volatile boolean onFuntime;
    private final String serverType = "anarchy";

    private TabHeaderTracker() {
    }

    public static TabHeaderTracker getInstance() {
        return INSTANCE;
    }

    public int getServerId() {
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
        FteLogger.info(FteLogger.TRACK, "TabHeaderTracker started, serverId=" + serverId + " onFuntime=" + onFuntime);
    }

    @Override
    public void stop() {
        FteLogger.info(FteLogger.TRACK, "TabHeaderTracker stopped");
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
            String hint = ServerDetector.getServerIdHint();
            int newServerId = ServerDetector.extractServerId(hint);
            if (newServerId != serverId) {
                serverId = newServerId;
            }
        } else {
            serverId = -1;
        }
    }
}
