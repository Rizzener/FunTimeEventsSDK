package com.funtimeevents.sdk.tracker;

import com.funtimeevents.sdk.api.FteConfig;
import com.funtimeevents.sdk.model.TabPlayersPayload;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.ban.BanTracker;
import com.funtimeevents.sdk.tracker.chat.ChatTracker;
import com.funtimeevents.sdk.tracker.dungeon.DungeonTracker;
import com.funtimeevents.sdk.tracker.hell.HellMapTracker;
import com.funtimeevents.sdk.tracker.mine.MineTracker;
import com.funtimeevents.sdk.tracker.spawn.SpawnEventTracker;
import com.funtimeevents.sdk.tracker.tab.TabTracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TrackerManager {

    private final List<Tracker> trackers = new CopyOnWriteArrayList<>();
    private final TabTracker tabTracker;
    private final PayloadSender sender;

    public TrackerManager(PayloadSender sender, FteConfig config) {
        this.sender = sender;
        trackers.add(TabHeaderTracker.getInstance());
        trackers.add(new ChatTracker());

        if (config.bansEnabled()) {
            trackers.add(new BanTracker(sender));
        }
        if (config.spawnEnabled()) {
            trackers.add(new SpawnEventTracker(sender));
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
        for (Tracker t : trackers) {
            t.start();
        }
    }

    public void stopAll() {
        for (Tracker t : trackers) {
            t.stop();
        }
    }

    public void tickAll() {
        for (Tracker t : trackers) {
            t.tick();
        }
        assembleTabPlayersPayload();
    }

    private void assembleTabPlayersPayload() {
        TabHeaderTracker header = TabHeaderTracker.getInstance();
        if (!header.isOnFuntime()) {
            return;
        }
        var players = tabTracker.getCurrentPlayers();
        if (players.isEmpty()) {
            return;
        }
        var payload = new TabPlayersPayload(header.getServerId(), header.getServerType(), players);
        sender.sendTabPlayers(payload);
    }
}
