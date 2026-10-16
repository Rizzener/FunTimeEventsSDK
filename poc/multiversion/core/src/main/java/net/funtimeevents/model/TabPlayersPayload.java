package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Request: full TAB player list for a server.
 */
public final class TabPlayersPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("players_list")
    private final List<ObservedPlayer> playersList;

    public TabPlayersPayload(int serverId, String serverType, List<ObservedPlayer> playersList) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.playersList = playersList;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public List<ObservedPlayer> playersList() { return playersList; }
}
