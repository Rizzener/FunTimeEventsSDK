package net.funtimeevents.poc.api;

import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.poc.core.PocCore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The very same source file is compiled against both Minecraft versions. */
public final class PocEventsAPI {
    private static volatile PocEventsAPI instance;
    private final PocCore core;

    private PocEventsAPI(String userAgent) {
        core = new PocCore(userAgent);
    }

    public static Builder builder() { return new Builder(); }

    public String probe() {
        if (Minecraft.getInstance() == null) {
            throw new IllegalStateException("Minecraft client missing");
        }
        return core.describe(Component.literal("shared-platform-ok").getString());
    }

    public String runtimeNamespace() {
        return FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
    }

    public static final class Builder {
        private String userAgent;

        public Builder userAgent(String value) { userAgent = value; return this; }

        public PocEventsAPI build() {
            synchronized (PocEventsAPI.class) {
                if (instance == null) instance = new PocEventsAPI(userAgent);
                return instance;
            }
        }
    }
}
