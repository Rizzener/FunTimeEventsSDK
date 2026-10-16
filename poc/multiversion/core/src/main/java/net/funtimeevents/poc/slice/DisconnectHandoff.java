package net.funtimeevents.poc.slice;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Internal connection identity handoff, not an additional platform contract. */
public final class DisconnectHandoff<T> {
    private final ConcurrentLinkedQueue<T> pending = new ConcurrentLinkedQueue<>();
    private T current; // Client thread only; producers only call offer.
    private long handled, stale;
    public void offer(T connection) { pending.add(connection); }
    public void join(T connection, Runnable disconnected) {
        drain(disconnected);
        // A new JOIN may overtake channelInactive from the old connection.
        // Finish that session now; its eventual callback must be stale.
        if (current != null && current != connection) {
            current = null;
            handled++;
            disconnected.run();
        }
        current = connection;
    }
    public void drain(Runnable disconnected) {
        T connection;
        while ((connection = pending.poll()) != null) {
            if (connection != current) { stale++; continue; }
            current = null;
            handled++;
            disconnected.run();
        }
    }
    public long handled() { return handled; }
    public long stale() { return stale; }
    public boolean empty() { return pending.isEmpty(); }
}
