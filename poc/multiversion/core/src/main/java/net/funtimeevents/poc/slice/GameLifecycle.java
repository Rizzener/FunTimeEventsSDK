package net.funtimeevents.poc.slice;

public interface GameLifecycle {
    void bind(Listener listener);

    interface Listener {
        void joined();
        void disconnected();
        void tick();
        void message(IncomingMessage message);
    }
}
