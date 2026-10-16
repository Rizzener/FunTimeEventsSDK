package net.funtimeevents.poc.api;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.poc.platform.FabricBanPlatform;
import net.funtimeevents.poc.slice.BanSliceRuntime;
import java.util.function.Consumer;

/** Experimental harness entrypoint; does not replace the release SDK API. */
public final class BanSliceAPI {
    private static volatile BanSliceRuntime instance;
    private static volatile int bindings;
    private BanSliceAPI() {}

    public static synchronized BanSliceRuntime start(Consumer<BanPayload> sender) {
        if (instance == null) {
            var platform = new FabricBanPlatform();
            var runtime = new BanSliceRuntime(platform, sender);
            platform.bind(runtime);
            instance = runtime;
            bindings++;
        }
        return instance;
    }

    public static int bindings() { return bindings; }
}
