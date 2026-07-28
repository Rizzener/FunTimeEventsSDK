package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class MineInfoResponse {

    @SerializedName("players_around")
    private List<TabPlayer> playersAround;

    @SerializedName("updated_at")
    private String updatedAt;

    public List<TabPlayer> playersAround() { return playersAround; }
    public String updatedAt() { return updatedAt; }
}
