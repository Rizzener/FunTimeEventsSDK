package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

public final class BanPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("player_name")
    private final String playerName;

    @SerializedName("reason")
    private final String reason;

    @SerializedName("end")
    private final String end;

    public BanPayload(int serverId, String serverType, String playerName, String reason, String end) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.playerName = playerName;
        this.reason = reason;
        this.end = end;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public String playerName() { return playerName; }
    public String reason() { return reason; }
    public String end() { return end; }
}
