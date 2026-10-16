package net.funtimeevents.bootstrap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.funtimeevents.api.FunTimeEventsAPI;
import net.funtimeevents.internal.platform.FabricPlatform;
import net.funtimeevents.internal.game.GameLifecycle;
import net.funtimeevents.internal.game.IncomingMessage;
import net.funtimeevents.internal.tracker.TrackerRuntime;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** Process callbacks stay registered once; the attached SDK and game session do not. */
public final class Bootstrap implements GameLifecycle.Listener {
    private static final Bootstrap INSTANCE = new Bootstrap();
    private final FabricPlatform platform = new FabricPlatform();
    private volatile TrackerRuntime active;
    private volatile boolean bound;
    private Runnable stoppingHook=()->{};
    private static final Set<CompletableFuture<?>> pending = new HashSet<>();
    private static boolean stopping;
    private Bootstrap() {}
    public static Bootstrap getInstance() { return INSTANCE; }
    public static void requireClientThread() {
        var client = Minecraft.getInstance();
        // Fabric's client entrypoint runs before Minecraft's event-loop owner is
        // initialized on 1.21.4. RenderSystem already owns that startup thread.
        if(client == null || (!client.isSameThread() && !RenderSystem.isOnRenderThread()))
            throw new IllegalStateException("SDK build/shutdown requires the Minecraft client thread");
    }
    /** Serializes ownership and game state without holding a lock while waiting. */
    public static <T> T onClientThread(Supplier<T> action) {
        var client=Minecraft.getInstance();
        if(client==null)throw new IllegalStateException("Minecraft client is unavailable");
        var result=new CompletableFuture<T>();
        synchronized(pending) {
            if(stopping)throw new IllegalStateException("Minecraft client is stopping");
            pending.add(result);
        }
        Runnable task=()->{
            try {if(!result.isDone())result.complete(action.get());}
            catch(Throwable error){result.completeExceptionally(error);}
            finally {synchronized(pending){pending.remove(result);}}
        };
        if(client.isSameThread()||RenderSystem.isOnRenderThread())task.run();
        else {
            try {client.execute(task);}
            catch(RuntimeException | Error error){synchronized(pending){pending.remove(result);}throw error;}
        }
        try {return result.join();}
        catch(CompletionException error) {
            if(error.getCause() instanceof RuntimeException cause)throw cause;
            if(error.getCause() instanceof Error cause)throw cause;
            throw error;
        }
    }
    private static void stopping() {
        requireClientThread();
        try {INSTANCE.stoppingHook.run();}
        finally {
            synchronized(pending) {
                stopping=true;
                for(var task:pending)task.completeExceptionally(new IllegalStateException("Minecraft client is stopping"));
                pending.clear();
            }
        }
    }
    public FabricPlatform platform() { return platform; }
    public TrackerRuntime runtime() { return active; }
    public void install(TrackerRuntime runtime) {
        install(runtime,()->{});
    }
    public void install(TrackerRuntime runtime,Runnable onStopping) {
        requireClientThread();
        if(active != null) throw new IllegalStateException("SDK runtime already attached");
        active = runtime;
        stoppingHook=onStopping;
        try {
            if(!bound) {
                platform.bind(this);
                ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopping());
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
