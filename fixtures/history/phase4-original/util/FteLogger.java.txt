package net.funtimeevents.util;

import net.funtimeevents.api.FteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured logger for the SDK.
 *
 * <p>Every log call requires a <strong>module tag</strong> as the first argument,
 * producing output with a readable subsystem prefix:
 * {@code [FTE:RELAY]}, {@code [FTE:TRACK]}, etc.
 *
 * <p>Output level is controlled via {@link FteConfig.LogLevel}.
 */
public final class FteLogger {

    /** SDK lifecycle, bootstrap, scheduler. */
    public static final String CORE  = "CORE";
    /** WebSocket connect/auth/watchdog/disconnect. */
    public static final String RELAY = "RELAY";
    /** HTTP requests/responses/gzip. */
    public static final String API   = "API";
    /** SSE streams, snapshot parsing. */
    public static final String CACHE = "CACHE";
    /** All trackers — start/stop/data output/errors. */
    public static final String TRACK = "TRACK";

    private static final Logger LOGGER = LoggerFactory.getLogger("FTE");
    private static volatile FteConfig.LogLevel logLevel = FteConfig.LogLevel.INFO;

    private FteLogger() {
    }

    /** Sets the global log level. Called automatically during SDK init. */
    public static void setLevel(FteConfig.LogLevel level) {
        logLevel = level;
    }

    /** Logs a debug message (shown only at {@link FteConfig.LogLevel#DEBUG}). */
    public static void debug(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.DEBUG.severity) {
            LOGGER.info("[FTE:{}] {}", module, msg);
        }
    }

    /** Logs an informational message. */
    public static void info(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.INFO.severity) {
            LOGGER.info("[FTE:{}] {}", module, msg);
        }
    }

    /** Logs a warning. */
    public static void warn(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.WARN.severity) {
            LOGGER.warn("[FTE:{}] {}", module, msg);
        }
    }

    /** Logs an error. */
    public static void error(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.ERROR.severity) {
            LOGGER.error("[FTE:{}] {}", module, msg);
        }
    }
}
