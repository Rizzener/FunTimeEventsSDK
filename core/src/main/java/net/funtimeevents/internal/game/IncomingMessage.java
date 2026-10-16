package net.funtimeevents.internal.game;

/** Valid only during the synchronous message callback; never retained by core. */
public interface IncomingMessage {
    String rawText();
    String plainText();
    String hoverText();
    default boolean isGame() { return true; }
}
