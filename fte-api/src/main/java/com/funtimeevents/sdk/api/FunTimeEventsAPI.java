package com.funtimeevents.sdk.api;

import com.funtimeevents.sdk.bootstrap.Bootstrap;
import com.funtimeevents.sdk.event.EventBus;
import com.funtimeevents.sdk.event.FteEvent;
import com.funtimeevents.sdk.net.ApiClient;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.TrackerManager;
import com.funtimeevents.sdk.util.FteLogger;

import java.util.List;
import java.util.function.Consumer;

public final class FunTimeEventsAPI {

    private static FunTimeEventsAPI instance;

    private FunTimeEventsAPI() {
    }

    public static FteConfig.Builder builder() {
        return new FteConfig.Builder();
    }

    static FunTimeEventsAPI create(FteConfig.Builder configBuilder) {
        if (instance != null) {
            FteLogger.warn("SDK already initialized, ignoring duplicate call");
            return instance;
        }
        FteConfig config = new FteConfig(configBuilder);

        if (config.userAgent() == null || config.userAgent().isBlank()) {
            throw new IllegalArgumentException("userAgent is required");
        }
        if (!config.offlineMode() && (config.apiKey() == null || config.apiKey().isBlank())) {
            throw new IllegalArgumentException("apiKey is required in online mode");
        }

        FteLogger.setLevel(config.logLevel());

        PayloadSender sender = null;
        if (!config.offlineMode()) {
            sender = new ApiClient(config.baseUrl(), config.apiKey(), config.userAgent());
        }

        TrackerManager trackerManager = new TrackerManager(sender, config);
        FteLogger.info("SDK initialized" + (config.offlineMode() ? " (offline mode)" : ""));
        Bootstrap.getInstance().start(trackerManager, config);
        instance = new FunTimeEventsAPI();
        return instance;
    }

    public static List<FteEvent> getEvents() {
        return EventBus.getInstance().drain();
    }

    public static void onEvent(Consumer<FteEvent> listener) {
        EventBus.getInstance().subscribe(listener);
    }
}
