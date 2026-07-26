package com.funtimeevents.sdk.tracker.dungeon;

import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.api.FunTimeEventsAPI;

final class DungeonBroadcaster {

    private DungeonBroadcaster() {
    }

    static void publishCopper(DungeonPayload payload) {
        FunTimeEventsAPI.sendCopperDungeon(payload);
    }

    static void publishWarden(DungeonPayload payload) {
        FunTimeEventsAPI.sendWardenCity(payload);
    }
}
