package net.funtimeevents.poc.trackers;

import net.funtimeevents.util.FteLogger;

/** Keep the checked tracker source stable while restoring the public log setting. */
public final class TrackLog {
    public static final String TRACK = "TRACK";
    private TrackLog() {}
    public static void info(String module, String message) { FteLogger.info(module, message); }
    public static void debug(String module, String message) { FteLogger.debug(module, message); }
    public static void warn(String module, String message) { FteLogger.warn(module, message); }
}
