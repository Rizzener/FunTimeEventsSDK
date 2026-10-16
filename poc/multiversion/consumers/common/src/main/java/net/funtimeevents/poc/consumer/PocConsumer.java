package net.funtimeevents.poc.consumer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.poc.api.PocEventsAPI;

/** Production smoke test. Exits after 20 client ticks, without joining any server. */
public final class PocConsumer implements ClientModInitializer {
    private int ticks;

    @Override
    public void onInitializeClient() {
        if(Boolean.getBoolean("fte.phase4.apiOnly")) {Phase4ApiOnly.start();return;}
        if (Boolean.getBoolean("fte.phase3")) { Phase3Consumer.start(); return; }
        if (Boolean.getBoolean("fte.phase2")) {
            Phase2Consumer.start();
            return;
        }
        var loader = FabricLoader.getInstance();
        if (loader.isDevelopmentEnvironment()) {
            throw new IllegalStateException("PoC requires a production run, not runClient");
        }
        var sdk = PocEventsAPI.builder().userAgent("PoCConsumer/1").build();
        var again = PocEventsAPI.builder().userAgent("PoCConsumer/1").build();
        if (sdk != again) throw new IllegalStateException("Duplicate SDK instance");
        String probe = sdk.probe();
        if (!probe.equals("core[PoCConsumer/1]:shared-platform-ok")) {
            throw new IllegalStateException(probe);
        }
        var container = loader.getModContainer("fte-multiversion-poc").orElseThrow();
        var origin = container.getOrigin();
        System.out.println("[FTE-POC] INITIALIZED namespace=" + sdk.runtimeNamespace()
            + " java=" + Runtime.version().feature() + " origin=" + origin.getKind()
            + " development=" + loader.isDevelopmentEnvironment() + " probe=" + probe);
        if (!origin.getKind().name().equals("NESTED")) {
            throw new IllegalStateException("SDK must be loaded only from nested jar: " + origin.getKind());
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks == 20) {
                System.out.println("[FTE-POC] RUNTIME_PASS ticks=" + ticks
                    + " namespace=" + sdk.runtimeNamespace());
                client.stop();
            }
        });
    }
}
