package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

public final class MineResponse {

    @SerializedName("server_id")
    private int serverId;

    @SerializedName("server_type")
    private String serverType;

    @SerializedName("rarity")
    private String rarity;

    @SerializedName("time")
    private int time;

    @SerializedName("mine_info")
    private MineInfoResponse mineInfo;

    @SerializedName("updated_at")
    private String updatedAt;

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public String rarity() { return rarity; }
    public int time() { return time; }
    public MineInfoResponse mineInfo() { return mineInfo; }
    public String updatedAt() { return updatedAt; }
}
