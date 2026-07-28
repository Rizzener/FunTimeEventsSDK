package net.funtimeevents.tracker;

import net.funtimeevents.api.FteConfig;
import net.funtimeevents.model.TabPlayersPayload;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.ban.BanTracker;
import net.funtimeevents.tracker.dungeon.DungeonTracker;
import net.funtimeevents.tracker.hell.HellMapTracker;
import net.funtimeevents.tracker.mine.MineTracker;
import net.funtimeevents.tracker.eventcoordinates.EventCoordinatesTracker;
import net.funtimeevents.tracker.tab.TabTracker;
import net.funtimeevents.tracker.server.ServerContext;
import net.funtimeevents.util.FteLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TrackerManager {

    private final List<Tracker> trackers = new CopyOnWriteArrayList<>();
    private final TabTracker tabTracker;
    private final PayloadSender sender;
    private final boolean tabPlayersEnabled;

    public TrackerManager(PayloadSender sender, FteConfig config) {
        this.sender = sender;
        this.tabPlayersEnabled = config.tabPlayersEnabled();
        trackers.add(ServerContext.getInstance());

        if (config.bansEnabled()) {
            trackers.add(new BanTracker(sender));
        }
        if (config.coordinatesEnabled()) {
            trackers.add(new EventCoordinatesTracker(sender));
        }
        if (config.dungeonEnabled()) {
            trackers.add(new DungeonTracker(sender));
        }
        if (config.hellMapEnabled()) {
            trackers.add(new HellMapTracker(sender));
        }
        if (config.mineEnabled()) {
            trackers.add(new MineTracker(sender));
        }

        tabTracker = new TabTracker();
        if (config.tabPlayersEnabled()) {
            trackers.add(tabTracker);
        }
    }

    public void startAll() {
        FteLogger.info(FteLogger.CORE, "Starting " + trackers.size() + " trackers");
        for (Tracker t : trackers) {
            t.start();
        }
    }

    public void stopAll() {
        FteLogger.info(FteLogger.CORE, "Stopping " + trackers.size() + " trackers");
        for (Tracker t : trackers) {
            t.stop();
        }
    }

    public void tickAll() {
        for (Tracker t : trackers) {
            t.tick();
        }
        sendTabPlayersPayload();
    }

    private void sendTabPlayersPayload() {
        if (!tabPlayersEnabled) {
            return;
        }
        ServerContext ctx = ServerContext.getInstance();
        if (!ctx.isOnFuntime()) {
            return;
        }
        var players = tabTracker.getCurrentPlayers();
        if (players.isEmpty()) {
            return;
        }
        var payload = new TabPlayersPayload(ctx.getServerId(), ctx.getServerType(), players);
        sender.sendTabPlayers(payload);
    }
}
