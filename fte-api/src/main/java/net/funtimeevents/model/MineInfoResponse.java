package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response: detailed info about a mine with players around.
 */
public final class MineInfoResponse {

    @SerializedName("players_around")
    private List<ObservedPlayer> playersAround;

    @SerializedName("updated_at")
    private String updatedAt;

    public List<ObservedPlayer> playersAround() { return playersAround; }
    public String updatedAt() { return updatedAt; }
}
