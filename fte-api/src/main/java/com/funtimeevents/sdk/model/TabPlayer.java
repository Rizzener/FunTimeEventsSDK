package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

public final class TabPlayer {

    @SerializedName("player_name")
    private final String playerName;

    @SerializedName("donate")
    private final String donate;

    @SerializedName("active")
    private final String active;

    public TabPlayer(String playerName, String donate, String active) {
        this.playerName = playerName;
        this.donate = donate;
        this.active = active;
    }

    public String playerName() { return playerName; }
    public String donate() { return donate; }
    public String active() { return active; }
}
