package net.funtimeevents.verification.consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.api.*;

/** Packaged as a second real mod, with its own nested copy of the same SDK. */
public final class PeerConsumer implements ClientModInitializer {
    private int ticks;
    private static RuntimeLease lease;
    private static java.util.concurrent.CompletableFuture<Void> concurrent;
    public static void releaseLease() {if(lease!=null){lease.close();lease.close();lease=null;}}
    public static boolean leasesVerified() {return concurrent!=null && concurrent.isDone() && !concurrent.isCompletedExceptionally();}
    public static void holdForStop(FteConfig.Builder config) {lease=config.buildLease();}
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(++ticks!=40)return;
            var first=builder().build();if(builder().build()!=first)throw new AssertionError("Peer runtime duplicated");
            lease=builder().buildLease();
            try {FunTimeEventsAPI.shutdown();throw new AssertionError("Legacy shutdown stopped leased runtime");}
            catch(IllegalStateException expected) {}
            var jobs=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
            var sharedClose=builder().buildLease();
            for(int i=0;i<24;i++)jobs.add(java.util.concurrent.CompletableFuture.runAsync(()->{
                var owned=builder().buildLease();
                try {
                    try {builder().userAgent("WorkerConflict").buildLease();throw new AssertionError("Worker conflict accepted");}
                    catch(IllegalStateException expected) {}
                    try {FunTimeEventsAPI.shutdown();throw new AssertionError("Worker shutdown accepted");}
                    catch(IllegalStateException expected) {}
                } finally {owned.close();owned.close();sharedClose.close();}
            }));
            concurrent=java.util.concurrent.CompletableFuture.allOf(jobs.toArray(java.util.concurrent.CompletableFuture[]::new));
            concurrent.whenComplete((v,error)->{
                if(error!=null)error.printStackTrace();
                else System.out.println("[HARDENING] LEASE_WORKERS_PASS workers=24 conflicts=24 shutdown_rejected=24");
            });
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
