package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class TabPlayersPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("players_list")
    private final List<TabPlayer> playersList;

    public TabPlayersPayload(int serverId, String serverType, List<TabPlayer> playersList) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.playersList = playersList;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public List<TabPlayer> playersList() { return playersList; }
}
