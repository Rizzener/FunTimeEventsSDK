package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class MinePlayersAroundPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("players_around")
    private final List<TabPlayer> playersAround;

    public MinePlayersAroundPayload(int serverId, String serverType, List<TabPlayer> playersAround) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.playersAround = playersAround;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public List<TabPlayer> playersAround() { return playersAround; }
}
