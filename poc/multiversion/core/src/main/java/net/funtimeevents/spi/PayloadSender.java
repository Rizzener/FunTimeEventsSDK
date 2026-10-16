package net.funtimeevents.spi;

import net.funtimeevents.model.BanPayload;
import net.funtimeevents.model.DungeonPayload;
import net.funtimeevents.model.EventCoordinatesPayload;
import net.funtimeevents.model.HellMapPayload;
import net.funtimeevents.model.MinePlayersAroundPayload;
import net.funtimeevents.model.TabPlayersPayload;

/**
 * Sends tracker payloads to the backend.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@code RelayClient} — WebSocket relay (default, WSS mode)</li>
 *   <li>{@code ApiClient} — HTTP POST (REST mode, when {@code .disableWebSocket()} is used)</li>
 * </ul>
 */
public interface PayloadSender {

    /** Sends the TAB player list for the current server. */
    void sendTabPlayers(TabPlayersPayload payload);
    /** Sends a detected ban event. */
    void sendBan(BanPayload payload);
    /** Sends copper dungeon scan results. */
    void sendCopperDungeon(DungeonPayload payload);
    /** Sends Warden City scan results. */
    void sendWardenCity(DungeonPayload payload);
    /** Sends Hell Map (boss bar) data. */
    void sendHellMap(HellMapPayload payload);
    /** Sends auto-mine player count around spawn. */
    void sendMinePlayers(MinePlayersAroundPayload payload);
    /** Sends detected event coordinates. */
    void sendEventCoordinates(EventCoordinatesPayload payload);
}
