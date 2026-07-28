package net.funtimeevents.spi;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.model.DungeonPayload;
import net.funtimeevents.model.EventCoordinatesPayload;
import net.funtimeevents.model.HellMapPayload;
import net.funtimeevents.model.MinePlayersAroundPayload;
import net.funtimeevents.model.TabPlayersPayload;

public interface PayloadSender {

    void sendTabPlayers(TabPlayersPayload payload);
    void sendBan(BanPayload payload);
    void sendCopperDungeon(DungeonPayload payload);
    void sendWardenCity(DungeonPayload payload);
    void sendHellMap(HellMapPayload payload);
    void sendMinePlayers(MinePlayersAroundPayload payload);
    void sendEventCoordinates(EventCoordinatesPayload payload);
}
