package com.funtimeevents.sdk.tracker.chat;

import com.funtimeevents.sdk.event.ChatMessageEvent;
import com.funtimeevents.sdk.event.EventBus;
import com.funtimeevents.sdk.tracker.Tracker;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public final class ChatTracker implements Tracker {

    private volatile boolean active;

    public ChatTracker() {
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (active) {
                String senderName = sender != null ? sender.getName() : "";
                EventBus.getInstance().publish(ChatMessageEvent.create(senderName, message.getString()));
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (active) {
                EventBus.getInstance().publish(ChatMessageEvent.create("", message.getString()));
            }
        });
    }

    @Override
    public void start() {
        active = true;
    }

    @Override
    public void stop() {
        active = false;
    }

    @Override
    public void tick() {
    }
}
