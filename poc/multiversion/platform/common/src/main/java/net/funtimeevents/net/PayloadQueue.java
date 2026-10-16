package net.funtimeevents.net;

import net.funtimeevents.model.*;
import net.funtimeevents.spi.SessionFence;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** Bounded snapshots (latest per server/type) and FIFO events. No serialized backlog. */
final class PayloadQueue {
    static final int SNAPSHOTS=64,EVENTS=256;
    static final long SNAPSHOT_TTL=TimeUnit.SECONDS.toNanos(15),EVENT_TTL=TimeUnit.SECONDS.toNanos(30);
    private static final Set<String> EVENT_TYPES=Set.of("bans","coordinates","/bans","/events/coordinates");
    record Key(String type,String serverType,int serverId) {}
    record Item(String type,Object body,Key key,boolean important,SessionFence.Ticket ticket,long offered) {}
    record Stats(int snapshots,int events,long coalesced,long evicted,long rejected,long expired,long stale,long discardedOnClose) {}
    private final Map<Key,Item> snapshots=new LinkedHashMap<>();
    private final ArrayDeque<Item> events=new ArrayDeque<>();
    private final LongSupplier clock;
    private volatile boolean closed;
    private volatile int eventBurst;
    private volatile long coalesced,evicted,rejected,expired,stale,discardedOnClose;
    PayloadQueue(){this(System::nanoTime);}
    PayloadQueue(LongSupplier clock){this.clock=clock;}
    synchronized boolean offer(String type,Object body,SessionFence.Ticket ticket) {
        if(closed){rejected++;return false;}
        if(!ticket.valid()){stale++;return false;}
        long now=clock.getAsLong();purge(now);
        boolean important=EVENT_TYPES.contains(type);
        Key key=important?null:key(type,body);
        var item=new Item(type,body,key,important,ticket,now);
        if(important) {
            if(events.size()>=EVENTS){rejected++;return false;}
            events.addLast(item);
        } else {
            if(snapshots.remove(key)!=null)coalesced++;
            else if(snapshots.size()>=SNAPSHOTS){snapshots.remove(snapshots.keySet().iterator().next());evicted++;}
            snapshots.put(key,item);
        }
        notifyAll();return true;
    }
    private static Key key(String type,Object body) {
        return switch(body) {
            case TabPlayersPayload p -> new Key(type,p.serverType(),p.serverId());
            case DungeonPayload p -> new Key(type,p.serverType(),p.serverId());
            case HellMapPayload p -> new Key(type,p.serverType(),p.serverId());
            case MinePlayersAroundPayload p -> new Key(type,p.serverType(),p.serverId());
            default -> throw new IllegalArgumentException("Unsupported observation type");
        };
    }
    private void purge(long now) {
        if(!events.isEmpty())for(var it=events.iterator();it.hasNext();) {var e=it.next();if(remove(e,now))it.remove();}
        if(!snapshots.isEmpty())for(var it=snapshots.values().iterator();it.hasNext();) {var e=it.next();if(remove(e,now))it.remove();}
    }
    private boolean remove(Item item,long now) {
        if(!item.ticket.valid()){stale++;return true;}
        if(now-item.offered>=(item.important?EVENT_TTL:SNAPSHOT_TTL)){expired++;return true;}
        return false;
    }
    synchronized Item pollNow() {
        purge(clock.getAsLong());
        if(!events.isEmpty()&&(eventBurst<8||snapshots.isEmpty())){eventBurst++;return events.removeFirst();}
        if(!snapshots.isEmpty()) {eventBurst=0;var it=snapshots.values().iterator();var item=it.next();it.remove();return item;}
        return null;
    }
    synchronized Item take(BooleanSupplier ready)throws InterruptedException {
        while(!closed) {
            purge(clock.getAsLong());
            if(ready.getAsBoolean()) {var item=pollNow();if(item!=null)return item;}
            wait(250);
        }
        return null;
    }
    synchronized void wake(){notifyAll();}
    synchronized void idleWait()throws InterruptedException {purge(clock.getAsLong());if(!closed)wait(250);}
    synchronized void close(){closed=true;discardedOnClose+=events.size()+snapshots.size();events.clear();snapshots.clear();notifyAll();}
    synchronized Stats stats(){purge(clock.getAsLong());return new Stats(snapshots.size(),events.size(),coalesced,evicted,rejected,expired,stale,discardedOnClose);}
}
