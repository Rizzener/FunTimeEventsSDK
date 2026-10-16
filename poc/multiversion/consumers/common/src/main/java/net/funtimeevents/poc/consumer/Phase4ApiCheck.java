package net.funtimeevents.poc.consumer;
import net.funtimeevents.api.*;
import net.funtimeevents.bootstrap.Bootstrap;
import net.funtimeevents.poc.trackers.TrackerRuntime;
import net.funtimeevents.spi.OfflineSender;

/** Exercises real public Builder on the client thread, including rollback. */
public final class Phase4ApiCheck {
    public static FteConfig.Builder offline() {
        return FunTimeEventsAPI.builder().userAgent("Phase4Consumer/1").offlineMode();
    }
    public static void run() {
        expect(IllegalArgumentException.class,()->FunTimeEventsAPI.builder().build());
        expect(IllegalArgumentException.class,()->FunTimeEventsAPI.builder().userAgent("NoKey").build());
        expect(IllegalArgumentException.class,()->offline().baseUrl("bad url").build());
        var b=Bootstrap.getInstance();
        var temporary=new TrackerRuntime(b.platform(),OfflineSender.INSTANCE,200);
        b.install(temporary);
        expect(IllegalStateException.class,()->offline().build());
        require(b.runtime()==temporary,"rollback detached another owner");b.detach();
        var first=offline().build();require(offline().build()==first,"equal config duplicated SDK");
        expect(IllegalStateException.class,()->offline().apiKey("different-secret-never-logged").build());
        expect(IllegalStateException.class,()->offline().userAgent("SecondConsumer/1").build());
        expect(IllegalStateException.class,()->offline().disableBansTracker().build());
        expect(IllegalStateException.class,()->offline().tickIntervalSeconds(5).build());
        require(offline().build()==first,"conflict changed runtime");
        FunTimeEventsAPI.shutdown();FunTimeEventsAPI.shutdown();
        require(b.runtime()==null && FunTimeEventsAPI.getEvents().isEmpty(),"shutdown left runtime/cache");
        // Each original disable method remains usable and yields a fresh SDK after shutdown.
        offline().disableScanTabPlayers().disableBansTracker().disableScanDungeon().disableScanHellMap()
            .disableScanMine().disableEventCoordinatesTracker().disableWebSocket().disableCompression().tickIntervalSeconds(1).build();
        FunTimeEventsAPI.shutdown();
        require(offline().build()!=first,"shutdown did not permit a fresh runtime");
        net.funtimeevents.poc.compat.LegacyApiProbe.exercise();
        require(FunTimeEventsAPI.fetchEvents(java.util.Map.of()).join()==null,"offline GET sent");
        require(FunTimeEventsAPI.solveCaptcha("AA==").join()==null,"offline captcha sent");
        System.out.println("[PHASE4A] BUILDER_PASS validation rollback equal conflict shutdown reinitialize flags offline");
    }
    private static void expect(Class<? extends Throwable> type,Runnable action) {
        try{action.run();}catch(Throwable error){if(type.isInstance(error))return;throw error;}
        throw new IllegalStateException("Expected "+type.getSimpleName());
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
