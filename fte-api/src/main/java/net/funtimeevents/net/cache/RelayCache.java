package net.funtimeevents.net.cache;

import net.funtimeevents.model.EventCoordinates;
import net.funtimeevents.model.EventResponse;
import net.funtimeevents.model.LootAreaResponse;
import net.funtimeevents.model.MineResponse;
import net.funtimeevents.model.Snapshot;
import net.funtimeevents.model.SystemInfo;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.GsonHolder;
import com.google.gson.Gson;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RelayCache {

    private static final Gson GSON = GsonHolder.INSTANCE;
    private static final double STALE_SECONDS = 5.0;

    private final Map<String, EventResponse> events = new ConcurrentHashMap<>();
    private final Map<String, EventResponse> userEvents = new ConcurrentHashMap<>();
    private final Map<String, MineResponse> mines = new ConcurrentHashMap<>();
    private final Map<Integer, LootAreaResponse> copperDungeons = new ConcurrentHashMap<>();
    private final Map<Integer, LootAreaResponse> wardenCities = new ConcurrentHashMap<>();
    private volatile SystemInfo systemInfo;
    private volatile double lastTs;
    private volatile long lastUpdateNanos;

    public void updateFromSnapshot(String json) {
        try {
            Snapshot snapshot = GSON.fromJson(json, Snapshot.class);
            if (!"snapshot".equals(snapshot.type())) return;

            lastTs = snapshot.ts();
            lastUpdateNanos = System.nanoTime();

            if (snapshot.events() != null) {
                events.clear();
                for (EventResponse e : snapshot.events()) {
                    events.put(systemEventKey(e), e);
                }
            }
            if (snapshot.userEvents() != null) {
                userEvents.clear();
                for (EventResponse e : snapshot.userEvents()) {
                    userEvents.put(userEventKey(e), e);
                }
            }
            if (snapshot.mines() != null) {
                mines.clear();
                for (MineResponse m : snapshot.mines()) {
                    mines.put(m.serverId() + "_" + m.rarity(), m);
                }
            }
            if (snapshot.copperDungeons() != null) {
                copperDungeons.clear();
                for (LootAreaResponse l : snapshot.copperDungeons()) {
                    copperDungeons.put(l.serverId(), l);
                }
            }
            if (snapshot.wardenCities() != null) {
                wardenCities.clear();
                for (LootAreaResponse l : snapshot.wardenCities()) {
                    wardenCities.put(l.serverId(), l);
                }
            }
            if (snapshot.systemInfo() != null) {
                systemInfo = snapshot.systemInfo();
            }

            FteLogger.debug(FteLogger.CACHE, "snapshot ts=" + lastTs + " events="
                    + (snapshot.events() != null ? snapshot.events().size() : 0) + " mines="
                    + (snapshot.mines() != null ? snapshot.mines().size() : 0) + " copper="
                    + (snapshot.copperDungeons() != null ? snapshot.copperDungeons().size() : 0) + " warden="
                    + (snapshot.wardenCities() != null ? snapshot.wardenCities().size() : 0));
        } catch (Exception e) {
            FteLogger.warn(FteLogger.CACHE, "failed to parse snapshot: " + e.getMessage());
        }
    }

    private static String systemEventKey(EventResponse e) {
        StringBuilder sb = new StringBuilder();
        if (e.serverType() != null) sb.append(e.serverType());
        sb.append(e.serverId());
        return sb.toString();
    }

    private static String userEventKey(EventResponse e) {
        StringBuilder sb = new StringBuilder();
        if (e.serverType() != null) sb.append(e.serverType());
        sb.append(e.serverId()).append(':');
        sb.append(e.eventId() != null ? e.eventId() : e.name() != null ? e.name() : "?");
        EventCoordinates c = e.coordinates();
        if (c != null) {
            sb.append(':').append(c.x()).append(':').append(c.y()).append(':').append(c.z());
        }
        return sb.toString();
    }

    private boolean isStale() {
        double age = (System.nanoTime() - lastUpdateNanos) / 1_000_000_000.0;
        return age > STALE_SECONDS;
    }

    public List<EventResponse> getEvents() {
        if (isStale()) { events.clear(); return List.of(); }
        return List.copyOf(events.values());
    }

    public List<EventResponse> getUserEvents() {
        if (isStale()) { userEvents.clear(); return List.of(); }
        return List.copyOf(userEvents.values());
    }

    public List<MineResponse> getMines() {
        if (isStale()) { mines.clear(); return List.of(); }
        return List.copyOf(mines.values());
    }

    public List<LootAreaResponse> getCopperDungeons() {
        if (isStale()) { copperDungeons.clear(); return List.of(); }
        return List.copyOf(copperDungeons.values());
    }

    public List<LootAreaResponse> getWardenCities() {
        if (isStale()) { wardenCities.clear(); return List.of(); }
        return List.copyOf(wardenCities.values());
    }

    public SystemInfo getSystemInfo() {
        return systemInfo;
    }
}
