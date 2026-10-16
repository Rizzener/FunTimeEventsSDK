package net.funtimeevents.poc.consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.api.FunTimeEventsAPI;
import net.funtimeevents.util.GsonHolder;
import java.nio.file.*;
import java.util.*;

/** Focused API/two-mod startup control; real tracker sessions are checked separately. */
public final class Phase4ApiOnly {
    private static int ticks;
    public static void start() {
        Phase4ApiCheck.run();
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(++ticks!=80)return;
            FunTimeEventsAPI.shutdown();
            var loader=FabricLoader.getInstance();
            var result=Map.of("status","PASS","namespace",loader.getMappingResolver().getCurrentRuntimeNamespace(),
                "java",Runtime.version().feature(),"sdk_version",FunTimeEventsAPI.getVersion(),
                "development",loader.isDevelopmentEnvironment(),"origin",loader.getModContainer("fte-multiversion-poc").orElseThrow().getOrigin().getKind().name());
            try{Files.writeString(Path.of("phase4-api-control.json"),GsonHolder.INSTANCE.toJson(result));}
            catch(Exception e){throw new IllegalStateException(e);}
            System.out.println("[PHASE4A] API_CONTROL_PASS "+result);client.stop();
        });
    }
}
