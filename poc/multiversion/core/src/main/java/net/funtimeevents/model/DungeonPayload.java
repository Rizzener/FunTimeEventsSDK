package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Request: dungeon scan results (server, type, players, chests).
 */
public final class DungeonPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("chests")
    private final List<ChestInfo> chests;

    @SerializedName("players")
    private final List<PlayerGearInfo> players;

    public DungeonPayload(int serverId, String serverType, List<ChestInfo> chests, List<PlayerGearInfo> players) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.chests = chests;
        this.players = players;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public List<ChestInfo> chests() { return chests; }
    public List<PlayerGearInfo> players() { return players; }
}
