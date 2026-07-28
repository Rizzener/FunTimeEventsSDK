package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

public final class ServerHistoryEntry {

    @SerializedName("server_id")
    private int serverId;

    @SerializedName("server_type")
    private String serverType;

    @SerializedName("first_seen")
    private String firstSeen;

    @SerializedName("last_seen")
    private String lastSeen;

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public String firstSeen() { return firstSeen; }
    public String lastSeen() { return lastSeen; }
}
