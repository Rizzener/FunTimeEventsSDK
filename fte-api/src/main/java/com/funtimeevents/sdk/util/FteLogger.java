package com.funtimeevents.sdk.util;

import com.funtimeevents.sdk.api.FteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FteLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("FTE");
    private static volatile FteConfig.LogLevel logLevel = FteConfig.LogLevel.INFO;

    private FteLogger() {
    }

    public static void setLevel(FteConfig.LogLevel level) {
        logLevel = level;
    }

    public static void debug(String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.DEBUG.severity) {
            LOGGER.info("[FTE] {}", msg);
        }
    }

    public static void info(String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.INFO.severity) {
            LOGGER.info("[FTE] {}", msg);
        }
    }

    public static void warn(String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.WARN.severity) {
            LOGGER.warn("[FTE] {}", msg);
        }
    }

    public static void error(String msg) {
        if (logLevel.severity >= FteConfig.LogLevel.ERROR.severity) {
            LOGGER.error("[FTE] {}", msg);
        }
    }
}
