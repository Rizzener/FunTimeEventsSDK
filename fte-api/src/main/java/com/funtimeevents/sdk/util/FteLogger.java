package com.funtimeevents.sdk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FteLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("FTE");

    private FteLogger() {
    }

    public static void info(String msg) {
        LOGGER.info("[FTE] {}", msg);
    }

    public static void warn(String msg) {
        LOGGER.warn("[FTE] {}", msg);
    }

    public static void error(String msg) {
        LOGGER.error("[FTE] {}", msg);
    }
}
