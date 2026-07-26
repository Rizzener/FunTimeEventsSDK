package com.funtimeevents.sdk.event;

import java.time.Instant;

public record ChatMessageEvent(String sender, String text, long timestamp) implements FteEvent {

    public String sender() {
        return sender;
    }

    public String text() {
        return text;
    }

    public static ChatMessageEvent create(String sender, String text) {
        return new ChatMessageEvent(sender, text, Instant.now().toEpochMilli());
    }
}
