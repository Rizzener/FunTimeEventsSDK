package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Immutable: a player observed in the TAB list or around the mine spawn.
 */
public final class ObservedPlayer {

    @SerializedName("player_name")
    private final String playerName;

    @SerializedName("donate")
    private final String donate;

    @SerializedName("active")
    private final String seenAt;

    public ObservedPlayer(String playerName, String donate, String seenAt) {
        this.playerName = playerName;
        this.donate = donate;
        this.seenAt = seenAt;
    }

    public String playerName() { return playerName; }
    public String donate() { return donate; }

    /** ISO-8601 timestamp when the player was observed. */
    public String seenAt() { return seenAt; }
}
