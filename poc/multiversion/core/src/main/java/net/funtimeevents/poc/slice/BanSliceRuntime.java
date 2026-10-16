package net.funtimeevents.poc.slice;

import net.funtimeevents.model.BanPayload;
import java.util.function.Consumer;

/** No executor, network or retained game objects; callbacks run on the client thread. */
public final class BanSliceRuntime implements GameLifecycle.Listener {
    private final ServerAccess access;
    private final ServerContext context = new ServerContext();
    private final BanTracker tracker;
    private final boolean bansEnabled;
    private volatile boolean joined;
    private volatile int joins;
    private volatile int disconnects;
    private volatile long ticks;
    private volatile long messages;

    public BanSliceRuntime(ServerAccess access, Consumer<BanPayload> sender) {
        this(access, sender, true);
    }

    public BanSliceRuntime(ServerAccess access, Consumer<BanPayload> sender, boolean bansEnabled) {
        this.access = access;
        this.bansEnabled = bansEnabled;
        tracker = new BanTracker(sender, context);
    }

    public ServerContext.State state() { return context.state(); }
    public ServerContext context() { return context; }
    public boolean isJoined() { return joined; }
    public int joins() { return joins; }
    public int disconnects() { return disconnects; }
    public long ticks() { return ticks; }
    public long messages() { return messages; }

    @Override public void joined() {
        if (joined) return;
        context.reset();
        joined = true;
        joins++;
        context.refresh(access);
        if (bansEnabled) tracker.start();
    }

    @Override public void disconnected() {
        if (!joined) return;
        joined = false;
        tracker.stop();
        context.reset();
        disconnects++;
    }

    @Override public void tick() {
        ticks++;
        if (joined) context.refresh(access);
    }

    @Override public void message(IncomingMessage message) {
        if (!joined) return;
        messages++;
        // Read the scoreboard after its preceding network packets were applied.
        context.refresh(access);
        tracker.handleMessage(message);
    }
}
