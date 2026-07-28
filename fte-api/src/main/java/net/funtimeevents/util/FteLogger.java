package net.funtimeevents.util;

import net.funtimeevents.api.FteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FteLogger {

    public static final String CORE  = "CORE";
    public static final String RELAY = "RELAY";
    public static final String API   = "API";
    public static final String CACHE = "CACHE";
    public static final String TRACK = "TRACK";

    private static final Logger LOGGER = LoggerFactory.getLogger("FTE");
    private static volatile FteConfig.LogLevel logLevel = FteConfig.LogLevel.INFO;

    private FteLogger() {
    }

    public static void setLevel(FteConfig.LogLevel level) {
        logLevel = level;
    }

    public static void debug(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.DEBUG.severity) {
            LOGGER.info("[FTE:{}] {}", module, msg);
        }
    }

    public static void info(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.INFO.severity) {
            LOGGER.info("[FTE:{}] {}", module, msg);
        }
    }

    public static void warn(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.WARN.severity) {
            LOGGER.warn("[FTE:{}] {}", module, msg);
        }
    }

    public static void error(String module, String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.ERROR.severity) {
            LOGGER.error("[FTE:{}] {}", module, msg);
        }
    }
}
