package net.funtimeevents.spi;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** A ticket is captured with the payload, never reconstructed from the next world. */
public final class SessionFence {
    private static final Ticket UNSCOPED=new Ticket(null,0,true);
    private volatile Ticket current=new Ticket(this,0,false);
    private volatile long generation;
    public Ticket capture(){return current;}
    public static Ticket unscoped(){return UNSCOPED;}
    public void begin(){change(true);}
    public void end(){change(false);}
    private void change(boolean active) {
        List<CompletableFuture<?>> cancel;
        synchronized(this) {
            var old=current;current=new Ticket(this,++generation,active);
            cancel=List.copyOf(old.inFlight);old.inFlight.clear();
        }
        // Never run cancellation callbacks under the lifecycle lock.
        for(var future:cancel)future.cancel(true);
    }
    public static final class Ticket {
        private final SessionFence owner;
        private final long generation;
        private final boolean active;
        private final Set<CompletableFuture<?>> inFlight=ConcurrentHashMap.newKeySet();
        private Ticket(SessionFence owner,long generation,boolean active){this.owner=owner;this.generation=generation;this.active=active;}
        public boolean valid(){return owner==null || (active && owner.current==this);}
        public long generation(){return generation;}
        public <T> CompletableFuture<T> dispatch(Supplier<CompletableFuture<T>> request) {
            if(owner==null)return request.get();
            synchronized(owner) {
                if(!valid())return CompletableFuture.failedFuture(new CancellationException("Obsolete game session"));
                var future=request.get();inFlight.add(future);future.whenComplete((value,error)->inFlight.remove(future));return future;
            }
        }
    }
}
