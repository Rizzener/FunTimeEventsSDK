package net.funtimeevents.bootstrap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.funtimeevents.api.FunTimeEventsAPI;
import net.funtimeevents.internal.platform.FabricPlatform;
import net.funtimeevents.internal.game.GameLifecycle;
import net.funtimeevents.internal.game.IncomingMessage;
import net.funtimeevents.internal.tracker.TrackerRuntime;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;

/** Process callbacks stay registered once; the attached SDK and game session do not. */
public final class Bootstrap implements GameLifecycle.Listener {
    private static final Bootstrap INSTANCE = new Bootstrap();
    private final FabricPlatform platform = new FabricPlatform();
    private volatile TrackerRuntime active;
    private volatile boolean bound;
    private Bootstrap() {}
    public static Bootstrap getInstance() { return INSTANCE; }
    public static void requireClientThread() {
        var client = Minecraft.getInstance();
        // Fabric's client entrypoint runs before Minecraft's event-loop owner is
        // initialized on 1.21.4. RenderSystem already owns that startup thread.
        if(client == null || (!client.isSameThread() && !RenderSystem.isOnRenderThread()))
            throw new IllegalStateException("SDK build/shutdown requires the Minecraft client thread");
    }
    public FabricPlatform platform() { return platform; }
    public TrackerRuntime runtime() { return active; }
    public void install(TrackerRuntime runtime) {
        requireClientThread();
        if(active != null) throw new IllegalStateException("SDK runtime already attached");
        active = runtime;
        try {
            if(!bound) {
                platform.bind(this);
                ClientLifecycleEvents.CLIENT_STOPPING.register(client -> FunTimeEventsAPI.shutdown());
                bound = true;
            } else if(FabricPlatform.isWorldConnected()) joined();
        } catch(RuntimeException | Error error) {
            active = null; runtime.disconnected(); throw error;
        }
    }
    public void detach() {
        requireClientThread();
        var previous = active;
        active = null;
        if(previous!=null) previous.disconnected();
    }
    public void joined() { var r=active; if(r!=null)r.joined(); }
    public void disconnected() { var r=active; if(r!=null)r.disconnected(); }
    public void tick() { var r=active; if(r!=null)r.tick(); }
    public void message(IncomingMessage message) { var r=active; if(r!=null)r.message(message); }
}
