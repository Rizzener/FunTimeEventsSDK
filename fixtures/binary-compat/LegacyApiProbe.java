package net.funtimeevents.poc.compat;
import net.funtimeevents.api.*;
import net.funtimeevents.model.*;
import java.util.*;

/** Compiled against the old release JAR, then executed with the migrated SDK only. */
public final class LegacyApiProbe {
    public static void exercise() {
        FunTimeEventsAPI.builder().userAgent("Phase4Consumer/1").offlineMode().build();
        if(FunTimeEventsAPI.getVersion().equals("unknown"))throw new AssertionError("version");
        if(!FunTimeEventsAPI.getEvents().isEmpty()||!FunTimeEventsAPI.getUserEvents().isEmpty()
            ||!FunTimeEventsAPI.getMines().isEmpty()||!FunTimeEventsAPI.getCopperDungeons().isEmpty()
            ||!FunTimeEventsAPI.getWardenCities().isEmpty()||FunTimeEventsAPI.getSystemInfo()!=null)throw new AssertionError("offline caches");
        if(FunTimeEventsAPI.fetchEvents(Map.of()).join()!=null||FunTimeEventsAPI.fetchUserEvents(Map.of()).join()!=null
            ||FunTimeEventsAPI.fetchMines(Map.of()).join()!=null||FunTimeEventsAPI.fetchPlayers(Map.of()).join()!=null
            ||FunTimeEventsAPI.fetchBans(Map.of()).join()!=null||FunTimeEventsAPI.fetchCopperDungeon().join()!=null
            ||FunTimeEventsAPI.fetchWardenCity().join()!=null||FunTimeEventsAPI.solveCaptcha("AA==").join()!=null)throw new AssertionError("offline requests");
        System.out.println("[PHASE4A] OLD_BINARY_PASS");
    }
}
