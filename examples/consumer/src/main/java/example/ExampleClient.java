package example;

import net.fabricmc.api.ClientModInitializer;
import net.funtimeevents.api.FunTimeEventsAPI;
import net.funtimeevents.api.RuntimeLease;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class ExampleClient implements ClientModInitializer {
    private RuntimeLease lease;
    @Override public void onInitializeClient() {
        lease=FunTimeEventsAPI.builder()
            .userAgent("ExampleMod/1.0")
            .apiKey(System.getProperty("fte.apiKey"))
            .buildLease();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> lease.close());
    }
}
