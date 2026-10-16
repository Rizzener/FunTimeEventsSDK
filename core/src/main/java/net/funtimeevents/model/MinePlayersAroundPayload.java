package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Request: players detected around the spawn auto-mine.
 */
public final class MinePlayersAroundPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("players_around")
    private final List<ObservedPlayer> playersAround;

    public MinePlayersAroundPayload(int serverId, String serverType, List<ObservedPlayer> playersAround) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.playersAround = playersAround;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public List<ObservedPlayer> playersAround() { return playersAround; }
}
