package com.funtimeevents.sdk.spi;

import com.funtimeevents.sdk.model.BanPayload;
import com.funtimeevents.sdk.model.CaptchaPayload;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.EventCoordinatesPayload;
import com.funtimeevents.sdk.model.HellMapPayload;
import com.funtimeevents.sdk.model.MinePlayersAroundPayload;
import com.funtimeevents.sdk.model.TabPlayersPayload;

public interface PayloadSender {

    void sendTabPlayers(TabPlayersPayload payload);
    void sendBan(BanPayload payload);
    void sendCopperDungeon(DungeonPayload payload);
    void sendWardenCity(DungeonPayload payload);
    void sendHellMap(HellMapPayload payload);
    void sendMinePlayers(MinePlayersAroundPayload payload);
    void sendEventCoordinates(EventCoordinatesPayload payload);
    void sendCaptcha(CaptchaPayload payload);
}
