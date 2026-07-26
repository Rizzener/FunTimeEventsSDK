package com.funtimeevents.sdk.tracker;

import com.funtimeevents.sdk.api.FunTimeEventsAPI;
import com.funtimeevents.sdk.model.TabPlayersPayload;
import com.funtimeevents.sdk.tracker.ban.BanTracker;
import com.funtimeevents.sdk.tracker.chat.ChatTracker;
import com.funtimeevents.sdk.tracker.dungeon.DungeonTracker;
import com.funtimeevents.sdk.tracker.hell.HellMapTracker;
import com.funtimeevents.sdk.tracker.mine.MineTracker;
import com.funtimeevents.sdk.tracker.tab.TabTracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.tracker.world.WorldPlayerTracker;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TrackerManager {

    private final List<Tracker> trackers = new CopyOnWriteArrayList<>();
    private final TabTracker tabTracker;

    public TrackerManager() {
        tabTracker = new TabTracker();
        trackers.add(TabHeaderTracker.getInstance());
        trackers.add(new ChatTracker());
        trackers.add(new WorldPlayerTracker());
        trackers.add(tabTracker);
        trackers.add(new BanTracker());
        trackers.add(new DungeonTracker());
        trackers.add(new HellMapTracker());
        trackers.add(new MineTracker());
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
        FunTimeEventsAPI.sendTabPlayers(payload);
    }
}
