package net.funtimeevents.verification.consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.api.*;

/** Packaged as a second real mod, with its own nested copy of the same SDK. */
public final class PeerConsumer implements ClientModInitializer {
    private int ticks;
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(++ticks!=40)return;
            var first=builder().build();if(builder().build()!=first)throw new AssertionError("Peer runtime duplicated");
            try {builder().apiKey("peer-conflicting-test-key").build();throw new AssertionError("Peer conflict silently accepted");}
            catch(IllegalStateException expected) {
                if(expected.getMessage().contains("peer-conflicting-test-key"))throw new AssertionError("Secret in error");
            }
            long copies=FabricLoader.getInstance().getAllMods().stream().filter(m->m.getMetadata().getId().equals("fte-api")).count();
            if(copies!=1)throw new AssertionError("Multiple loaded SDKs: "+copies);
            System.out.println("[PHASE4A] PEER_MOD_PASS sdk_copies="+copies+" equal_shared=true conflict_rejected=true");
        });
    }
    private FteConfig.Builder builder() {
        var b=FunTimeEventsAPI.builder().userAgent("Phase4Consumer/1");
        if("a".equals(System.getProperty("fte.phase4.stage")))return b.offlineMode();
        b.apiKey("phase4-local-key").baseUrl(System.getProperty("fte.phase4.backend")+"/v1/");
        String mode=System.getProperty("fte.phase4.mode","ws");if(!mode.equals("ws"))b.disableWebSocket();if(mode.equals("plain"))b.disableCompression();
        return b;
    }
}
