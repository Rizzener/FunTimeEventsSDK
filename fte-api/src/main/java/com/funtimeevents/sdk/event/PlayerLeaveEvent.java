package com.funtimeevents.sdk.event;

import java.util.UUID;

public record PlayerLeaveEvent(String name, UUID uuid, Source source, long timestamp) {

    public static PlayerLeaveEvent create(String name, UUID uuid, Source source) {
        return new PlayerLeaveEvent(name, uuid, source, System.currentTimeMillis());
    }
}
