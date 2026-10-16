package net.funtimeevents.api;

/** Consumer-only test hook for a forced generation transition without process exit. */
public final class LeaseGenerationFixture {
    public static void invalidate() { FunTimeEventsAPI.clientStopping(); }
}
