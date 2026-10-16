package net.funtimeevents.net.cache;
import net.funtimeevents.model.*;
import net.funtimeevents.util.*;
import java.util.*;
import java.util.function.*;

/** Whole lists are built off-thread and published by a single volatile state write. */
public final class RelayCache {
    private record State(List<EventResponse> events,List<EventResponse> users,List<MineResponse> mines,
        List<LootAreaResponse> copper,List<LootAreaResponse> warden,SystemInfo info,long updated) {}
    private volatile State state=new State(List.of(),List.of(),List.of(),List.of(),List.of(),null,0);
    public void updateFromSnapshot(String json){updateFromSnapshot(json,Runnable::run);}
    /** Transport supplies a short identity guard; parsing never holds its lifecycle lock. */
    public void updateFromSnapshot(String json,Consumer<Runnable> publish) {
        try {
            Snapshot s=GsonHolder.INSTANCE.fromJson(json,Snapshot.class);if(s==null||!"snapshot".equals(s.type()))return;
            var events=dedupe(s.events(),RelayCache::systemEventKey);var users=dedupe(s.userEvents(),RelayCache::userEventKey);
            var mines=dedupe(s.mines(),m->m.serverId()+"_"+m.rarity());
            var copper=dedupe(s.copperDungeons(),LootAreaResponse::serverId);var warden=dedupe(s.wardenCities(),LootAreaResponse::serverId);
            publish.accept(()->{synchronized(this){var old=state;state=new State(events==null?old.events:events,users==null?old.users:users,
                mines==null?old.mines:mines,copper==null?old.copper:copper,warden==null?old.warden:warden,
                s.systemInfo()==null?old.info:s.systemInfo(),System.nanoTime());}});
        }catch(RuntimeException e){FteLogger.warn(FteLogger.CACHE,"Invalid snapshot: "+e.getClass().getSimpleName());}
    }
    private static <T,K>List<T> dedupe(List<T> items,Function<T,K> key) {
        if(items==null)return null;Map<K,T> map=new LinkedHashMap<>();for(T item:items)map.put(key.apply(Objects.requireNonNull(item)),item);return List.copyOf(map.values());
    }
    private static String systemEventKey(EventResponse e){return(e.serverType()==null?"":e.serverType())+e.serverId();}
    private static String userEventKey(EventResponse e) {
        String key=systemEventKey(e)+":"+(e.eventId()!=null?e.eventId():e.name()!=null?e.name():"?");
        if(e.coordinates()!=null)key+=":"+e.coordinates().x()+":"+e.coordinates().y()+":"+e.coordinates().z();return key;
    }
    private static boolean stale(State s){return s.updated==0||System.nanoTime()-s.updated>5_000_000_000L;}
    public List<EventResponse> getEvents(){var s=state;return stale(s)?List.of():s.events;}
    public List<EventResponse> getUserEvents(){var s=state;return stale(s)?List.of():s.users;}
    public List<MineResponse> getMines(){var s=state;return stale(s)?List.of():s.mines;}
    public List<LootAreaResponse> getCopperDungeons(){var s=state;return stale(s)?List.of():s.copper;}
    public List<LootAreaResponse> getWardenCities(){var s=state;return stale(s)?List.of():s.warden;}
    // Preserve the original SystemInfo getter semantics (last received info).
    public SystemInfo getSystemInfo(){return state.info;}
}
