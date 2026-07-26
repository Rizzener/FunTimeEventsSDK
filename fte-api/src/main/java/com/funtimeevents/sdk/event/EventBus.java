package com.funtimeevents.sdk.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final Queue<FteEvent> queue = new ConcurrentLinkedQueue<>();
    private final List<Consumer<FteEvent>> subscribers = new CopyOnWriteArrayList<>();

    private EventBus() {
    }

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public void publish(FteEvent event) {
        queue.add(event);
        for (Consumer<FteEvent> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }

    public List<FteEvent> drain() {
        return drain(Integer.MAX_VALUE);
    }

    public List<FteEvent> drain(int max) {
        List<FteEvent> events = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            FteEvent event = queue.poll();
            if (event == null) {
                break;
            }
            events.add(event);
        }
        return events;
    }

    public void subscribe(Consumer<FteEvent> listener) {
        subscribers.add(listener);
    }

    public void unsubscribe(Consumer<FteEvent> listener) {
        subscribers.remove(listener);
    }
}
