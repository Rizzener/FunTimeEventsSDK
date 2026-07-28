package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response: a dungeon server state (players and chests).
 */
public final class LootAreaResponse {

    @SerializedName("server_id")
    private int serverId;

    @SerializedName("server_type")
    private String serverType;

    @SerializedName("chests")
    private List<ChestResponse> chests;

    @SerializedName("players")
    private List<PlayerGearInfo> players;

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public List<ChestResponse> chests() { return chests; }
    public List<PlayerGearInfo> players() { return players; }
}
