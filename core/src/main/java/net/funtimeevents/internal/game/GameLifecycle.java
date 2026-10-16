package net.funtimeevents.internal.game;

public interface GameLifecycle {
    void bind(Listener listener);

    interface Listener {
        void joined();
        void disconnected();
        void tick();
        void message(IncomingMessage message);
    }
}
