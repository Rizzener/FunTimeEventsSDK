package net.funtimeevents.spi;
import net.funtimeevents.model.*;

/** Offline collects through the same trackers and deliberately performs no I/O. */
public enum OfflineSender implements PayloadSender {
    INSTANCE;
    public void sendTabPlayers(TabPlayersPayload p) {}
    public void sendBan(BanPayload p) {}
    public void sendCopperDungeon(DungeonPayload p) {}
    public void sendWardenCity(DungeonPayload p) {}
    public void sendHellMap(HellMapPayload p) {}
    public void sendMinePlayers(MinePlayersAroundPayload p) {}
    public void sendEventCoordinates(EventCoordinatesPayload p) {}
}
