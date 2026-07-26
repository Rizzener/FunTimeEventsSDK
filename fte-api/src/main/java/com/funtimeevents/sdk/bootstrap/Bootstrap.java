package com.funtimeevents.sdk.bootstrap;

import com.funtimeevents.sdk.scheduler.Scheduler;
import com.funtimeevents.sdk.tracker.TrackerManager;
import com.funtimeevents.sdk.util.FteLogger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

public final class Bootstrap {

    private static final Bootstrap INSTANCE = new Bootstrap();

    private final TrackerManager trackerManager;
    private final Scheduler scheduler;
    private boolean running;

    private Bootstrap() {
        this.trackerManager = new TrackerManager();
        this.scheduler = new Scheduler();
    }

    public static Bootstrap getInstance() {
        return INSTANCE;
    }

    public void start() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            FteLogger.error("MinecraftClient not available — is Fabric loaded?");
            return;
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client_) -> onWorldJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client_) -> onWorldLeave());
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        if (client.world != null) {
            onWorldJoin();
        } else {
            FteLogger.info("Waiting for world...");
        }
    }

    private void onClientTick(MinecraftClient client) {
        if (running) {
            scheduler.tick();
        }
    }

    private void onWorldJoin() {
        if (running) {
            return;
        }
        running = true;
        FteLogger.info("Connected to world");
        trackerManager.startAll();
        scheduler.start(() -> {
            var world = MinecraftClient.getInstance().world;
            if (world != null) {
                FteLogger.info("World: " + world.getRegistryKey().getValue());
            }
            trackerManager.tickAll();
        });
    }

    private void onWorldLeave() {
        if (!running) {
            return;
        }
        running = false;
        FteLogger.info("Disconnected from world");
        scheduler.stop();
        trackerManager.stopAll();
    }
}
