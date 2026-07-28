package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response: a player's metadata with server history.
 */
public final class PlayerDataResponse {

    @SerializedName("player_name")
    private String playerName;

    @SerializedName("donate")
    private String donate;

    @SerializedName("active")
    private String active;

    @SerializedName("server_id")
    private int serverId;

    @SerializedName("server_type")
    private String serverType;

    @SerializedName("server_history")
    private List<ServerHistoryEntry> serverHistory;

    public String playerName() { return playerName; }
    public String donate() { return donate; }
    public String active() { return active; }
    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public List<ServerHistoryEntry> serverHistory() { return serverHistory; }
}
