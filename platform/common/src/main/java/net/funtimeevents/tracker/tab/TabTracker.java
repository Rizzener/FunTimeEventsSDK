package net.funtimeevents.tracker.tab;

import net.funtimeevents.model.ObservedPlayer;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.internal.tracker.TrackLog;
import net.funtimeevents.util.PlayerNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TabTracker implements Tracker {

    @Override
    public void start() {
        TrackLog.info(TrackLog.TRACK, "TabTracker started");
    }

    @Override
    public void stop() {
        TrackLog.info(TrackLog.TRACK, "TabTracker stopped");
    }

    @Override
    public void tick() {
    }

    public List<ObservedPlayer> getCurrentPlayers() {
        var handler = Minecraft.getInstance().getConnection();
        if (handler == null) {
            return Collections.emptyList();
        }
        String now = Instant.now().toString();
        List<ObservedPlayer> result = new ArrayList<>();
        for (PlayerInfo entry : handler.getOnlinePlayers()) {
            var info = PlayerNameUtil.resolvePlayer(entry);
            if (info == null) continue;
            result.add(new ObservedPlayer(info.name(), info.donate(), now));
        }
        return result;
    }
}
