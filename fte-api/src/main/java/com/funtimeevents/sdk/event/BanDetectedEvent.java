package com.funtimeevents.sdk.event;

import java.time.Instant;

public record BanDetectedEvent(String playerName, String rawHoverText, long timestamp) implements FteEvent {

    public static BanDetectedEvent create(String playerName, String rawHoverText) {
        return new BanDetectedEvent(playerName, rawHoverText, Instant.now().toEpochMilli());
    }
}
