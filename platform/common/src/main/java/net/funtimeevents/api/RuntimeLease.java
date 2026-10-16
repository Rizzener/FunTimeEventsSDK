package net.funtimeevents.api;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ownership of one SDK runtime generation. Close when the consumer mod stops
 * using the SDK, not on game disconnect. Closing is thread safe and idempotent.
 * The last lease stops the runtime unless a legacy Builder.build() pins it.
 */
public final class RuntimeLease implements AutoCloseable {
    private final FunTimeEventsAPI generation;
    private final AtomicBoolean closed = new AtomicBoolean();

    RuntimeLease(FunTimeEventsAPI generation) { this.generation = generation; }

    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        try { FunTimeEventsAPI.release(generation); }
        catch(RuntimeException | Error error) { closed.set(false);throw error; }
    }
}
