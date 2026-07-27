package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

public final class BanResponse {

    @SerializedName("server_id")
    private int serverId;

    @SerializedName("server_type")
    private String serverType;

    @SerializedName("player_name")
    private String playerName;

    @SerializedName("reason")
    private String reason;

    @SerializedName("end")
    private String end;

    @SerializedName("banned_at")
    private String bannedAt;

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public String playerName() { return playerName; }
    public String reason() { return reason; }
    public String end() { return end; }
    public String bannedAt() { return bannedAt; }
}
