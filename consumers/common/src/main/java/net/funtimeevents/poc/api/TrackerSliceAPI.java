package net.funtimeevents.poc.api;

import net.funtimeevents.internal.platform.FabricPlatform;
import net.funtimeevents.internal.tracker.TrackerRuntime;
import net.funtimeevents.spi.PayloadSender;

/** Isolated migration entrypoint; the release public Builder remains untouched. */
public final class TrackerSliceAPI {
    private static volatile TrackerRuntime instance;
    private static volatile FabricPlatform platform;
    private TrackerSliceAPI() {}
    public static synchronized TrackerRuntime start(PayloadSender sink) {
        if (instance != null) return instance;
        platform = new FabricPlatform();
        instance = new TrackerRuntime(platform, sink, 200);
        platform.bind(instance);
        return instance;
    }
    public static long staleDisconnects() { return platform.staleDisconnects(); }
    public static boolean handoffEmpty() { return platform.handoffEmpty(); }
}
