package com.funtimeevents.sdk.event;

public sealed interface FteEvent permits ChatMessageEvent, BanDetectedEvent {

    long timestamp();
}
