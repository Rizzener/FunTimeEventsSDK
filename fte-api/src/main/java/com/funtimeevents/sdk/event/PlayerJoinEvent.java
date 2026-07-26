package com.funtimeevents.sdk.event;

import java.util.UUID;

public record PlayerJoinEvent(String name, UUID uuid, Source source, long timestamp) {

    public static PlayerJoinEvent create(String name, UUID uuid, Source source) {
        return new PlayerJoinEvent(name, uuid, source, System.currentTimeMillis());
    }
}
