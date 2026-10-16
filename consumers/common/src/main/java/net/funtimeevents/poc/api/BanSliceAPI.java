package net.funtimeevents.poc.api;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.internal.platform.FabricPlatform;
import net.funtimeevents.internal.game.GameSessionRuntime;
import java.util.function.Consumer;

/** Experimental harness entrypoint; does not replace the release SDK API. */
public final class BanSliceAPI {
    private static volatile GameSessionRuntime instance;
    private static volatile int bindings;
    private BanSliceAPI() {}

    public static synchronized GameSessionRuntime start(Consumer<BanPayload> sender) {
        if (instance == null) {
            var platform = new FabricPlatform();
            var runtime = new GameSessionRuntime(platform, sender);
            platform.bind(runtime);
            instance = runtime;
            bindings++;
        }
        return instance;
    }

    public static int bindings() { return bindings; }
}
