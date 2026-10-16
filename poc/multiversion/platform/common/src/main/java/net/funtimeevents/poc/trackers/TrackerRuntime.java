package net.funtimeevents.poc.trackers;

import net.funtimeevents.poc.slice.*;
import net.funtimeevents.scheduler.Scheduler;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.spi.SessionFence;
import net.funtimeevents.model.TabPlayersPayload;
import net.funtimeevents.tracker.dungeon.DungeonTracker;
import net.funtimeevents.tracker.hell.HellMapTracker;
import net.funtimeevents.tracker.tab.TabTracker;
import net.funtimeevents.tracker.mine.MineTracker;
import net.funtimeevents.tracker.eventcoordinates.EventCoordinatesTracker;
import net.funtimeevents.util.PlayerNameUtil;

/** One lifecycle owner, original Scheduler and tracker cadences. No network client. */
public final class TrackerRuntime implements GameLifecycle.Listener {
    private final BanSliceRuntime bans;
    private final DungeonTracker dungeon;
    private final HellMapTracker hell;
    private final TabTracker tab = new TabTracker();
    private final MineTracker mine;
    private final EventCoordinatesTracker coordinates;
    private final PayloadSender sink;
    private final Scheduler scheduler;
    private final boolean dungeonEnabled, hellEnabled, tabEnabled, mineEnabled, coordinatesEnabled;
    private final SessionFence sessionFence;
    private volatile int sessionTicks;
    private volatile int periodic;

    public TrackerRuntime(ServerAccess access, PayloadSender sink, int intervalTicks) {
        this(access, sink, intervalTicks, true, true, true, true, true, true);
    }
    public TrackerRuntime(ServerAccess access, PayloadSender sink, int intervalTicks,
            boolean bansEnabled, boolean dungeonEnabled, boolean hellEnabled,
            boolean tabEnabled, boolean mineEnabled, boolean coordinatesEnabled) {
        this(access,sink,intervalTicks,bansEnabled,dungeonEnabled,hellEnabled,tabEnabled,mineEnabled,coordinatesEnabled,null);
    }
    public TrackerRuntime(ServerAccess access, PayloadSender sink, int intervalTicks,
            boolean bansEnabled, boolean dungeonEnabled, boolean hellEnabled,
            boolean tabEnabled, boolean mineEnabled, boolean coordinatesEnabled,SessionFence sessionFence) {
        this.sink = sink;
        this.sessionFence=sessionFence;
        this.dungeonEnabled=dungeonEnabled; this.hellEnabled=hellEnabled;
        this.tabEnabled=tabEnabled; this.mineEnabled=mineEnabled; this.coordinatesEnabled=coordinatesEnabled;
        bans = new BanSliceRuntime(access, sink::sendBan, bansEnabled);
        dungeon = new DungeonTracker(sink, bans.context());
        hell = new HellMapTracker(sink, bans.context());
        mine = new MineTracker(sink, bans.context());
        coordinates = new EventCoordinatesTracker(sink, bans.context());
        scheduler = new Scheduler(intervalTicks);
    }
    public BanSliceRuntime bans() { return bans; }
    public int sessionTicks() { return sessionTicks; }
    public int periodic() { return periodic; }
    @Override public void joined() {
        if (bans.isJoined()) return;
        if(sessionFence!=null)sessionFence.begin();
        PlayerNameUtil.clearSession();
        bans.joined(); sessionTicks = 0;
        if(coordinatesEnabled) coordinates.start();
        if(dungeonEnabled) dungeon.start();
        if(hellEnabled) hell.start();
        if(mineEnabled) mine.start();
        if(tabEnabled) tab.start();
        scheduler.start(this::periodicTick);
    }
    @Override public void disconnected() {
        if (!bans.isJoined()) return;
        if(sessionFence!=null)sessionFence.end();
        scheduler.stop();
        if(coordinatesEnabled)coordinates.stop();if(dungeonEnabled)dungeon.stop();if(hellEnabled)hell.stop();
        if(mineEnabled)mine.stop();if(tabEnabled)tab.stop();
        PlayerNameUtil.clearSession(); bans.disconnected();
    }
    @Override public void tick() {
        bans.tick();
        if (!bans.isJoined()) return;
        sessionTicks++;
        if(hellEnabled) hell.clientTick();
        scheduler.tick();
    }
    @Override public void message(IncomingMessage message) {
        bans.message(message);
        if(coordinatesEnabled) coordinates.handleMessage(message);
    }
    private void periodicTick() {
        periodic++;
        long at = TrackerMetrics.begin(); if(dungeonEnabled) dungeon.tick(); TrackerMetrics.end("dungeon", at);
        at = TrackerMetrics.begin(); if(mineEnabled) mine.tick(); TrackerMetrics.end("mine", at);
        at = TrackerMetrics.begin();
        // Original TrackerManager permits unresolved ID for TAB; preserve it.
        if (tabEnabled && bans.context().isOnFuntime()) {
            var players = tab.getCurrentPlayers();
            if (!players.isEmpty()) sink.sendTabPlayers(new TabPlayersPayload(
                bans.context().getServerId(), bans.context().getServerType(), players));
        }
        TrackerMetrics.end("tab", at);
    }
}
