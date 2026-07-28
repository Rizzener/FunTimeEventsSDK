package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response: an active mine with rarity, time, and optional mine info.
 */
public final class MineResponse {

    @SerializedName("server_id")
    private int serverId;

    @SerializedName("server_type")
    private String serverType;

    @SerializedName("rarity")
    private String rarity;

    @SerializedName("time")
    private int timeLeft;

    @SerializedName("mine_info")
    private MineInfoResponse mineInfo;

    @SerializedName("updated_at")
    private String updatedAt;

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public String rarity() { return rarity; }

    /** Seconds remaining until the mine expires. */
    public int timeLeft() { return timeLeft; }
    public MineInfoResponse mineInfo() { return mineInfo; }
    public String updatedAt() { return updatedAt; }
}
