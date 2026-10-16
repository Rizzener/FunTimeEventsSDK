package net.funtimeevents.poc.platform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.funtimeevents.poc.slice.GameLifecycle;
import net.funtimeevents.poc.slice.IncomingMessage;
import net.funtimeevents.poc.slice.ServerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.funtimeevents.poc.slice.DisconnectHandoff;

public final class FabricBanPlatform implements GameLifecycle, ServerAccess {
    private volatile boolean bound;
    private final DisconnectHandoff<ClientPacketListener> disconnects = new DisconnectHandoff<>();
    private Runnable disconnectedAction; // Bound once; no method-reference allocation each tick.

    @Override public synchronized void bind(Listener listener) {
        if (bound) throw new IllegalStateException("Callbacks already bound");
        bound = true;
        disconnectedAction = listener::disconnected;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            requireClientThread();
            disconnects.join(handler, disconnectedAction);
            listener.joined();
        });
        // channelInactive may run on Netty. Minecraft 26.3 clears client.execute()
        // tasks during disconnect, so this handoff must survive that queue clear.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnects.offer(handler));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            requireClientThread();
            drainDisconnects(listener);
            listener.tick();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> deliver(listener, message, true));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, type, timestamp) -> deliver(listener, message, false));
        if (isWorldConnected()) {
            disconnects.join(Minecraft.getInstance().getConnection(), disconnectedAction);
            listener.joined();
        }
    }
    public static boolean isWorldConnected() {
        var client=Minecraft.getInstance();
        return client!=null&&client.level!=null&&client.getConnection()!=null&&client.getConnection().getConnection().isConnected();
    }

    private void drainDisconnects(Listener listener) {
        disconnects.drain(disconnectedAction);
    }

    public long staleDisconnects() { return disconnects.stale(); }
    public boolean handoffEmpty() { return disconnects.empty(); }

    private void deliver(Listener listener, Component message, boolean game) {
        requireClientThread();
        drainDisconnects(listener);
        listener.message(new IncomingMessage() {
            @Override public String rawText() { return MinecraftText.raw(message); }
            @Override public String plainText() { return message.getString(); }
            @Override public String hoverText() { return MinecraftText.hover(message); }
            @Override public boolean isGame() { return game; }
        });
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("Fabric callback outside client thread");
        }
    }

    @Override public String serverAddress() {
        var data = Minecraft.getInstance().getCurrentServer();
        return data == null ? null : data.ip;
    }

    @Override public String sidebarTitle() {
        var world = Minecraft.getInstance().level;
        if (world == null) return null;
        var objective = world.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective == null ? null : objective.getDisplayName().getString();
    }
}
