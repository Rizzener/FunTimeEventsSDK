package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Request: Hell Map boss bar data (monster count, text).
 */
public final class HellMapPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("mobs_count")
    private final int mobsCount;

    public HellMapPayload(int serverId, String serverType, int mobsCount) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.mobsCount = mobsCount;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public int mobsCount() { return mobsCount; }
}
