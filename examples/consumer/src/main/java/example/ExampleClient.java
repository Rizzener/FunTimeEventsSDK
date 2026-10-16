package example;

import net.fabricmc.api.ClientModInitializer;
import net.funtimeevents.api.FunTimeEventsAPI;

public final class ExampleClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        FunTimeEventsAPI.builder()
            .userAgent("ExampleMod/1.0")
            .apiKey(System.getProperty("fte.apiKey"))
            .build();
    }
}
