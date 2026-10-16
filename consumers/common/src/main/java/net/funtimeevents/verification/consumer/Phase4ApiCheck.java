package net.funtimeevents.verification.consumer;
import net.funtimeevents.api.*;
import net.funtimeevents.bootstrap.Bootstrap;
import net.funtimeevents.internal.tracker.TrackerRuntime;
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
        var lease=offline().buildLease();var generation=b.runtime();
        var second=offline().buildLease();
        expect(IllegalStateException.class,FunTimeEventsAPI::shutdown);
        expect(IllegalStateException.class,()->offline().userAgent("ConflictingLease").buildLease());
        lease.close();lease.close();require(b.runtime()==generation,"first lease stopped peer");
        second.close();require(b.runtime()==null,"last lease did not stop runtime");
        var stale=offline().buildLease();
        net.funtimeevents.api.LeaseGenerationFixture.invalidate();
        var replacement=offline().buildLease();var next=b.runtime();
        stale.close();require(b.runtime()==next,"stale generation stopped replacement");
        offline().build();replacement.close();
        require(b.runtime()==next,"lease ignored legacy lifetime pin");
        FunTimeEventsAPI.shutdown();
        System.out.println("[HARDENING] LEASE_GENERATION_PASS last_close legacy_pin stale_close shutdown_conflict");
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
