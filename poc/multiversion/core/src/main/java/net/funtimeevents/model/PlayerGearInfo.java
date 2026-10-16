package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Immutable: a single player's gear in a dungeon zone (name, donate, armor slots, invisibility).
 */
public final class PlayerGearInfo {

    @SerializedName("player_name")
    private final String playerName;

    @SerializedName("donate")
    private final String donate;

    @SerializedName("helmet")
    private final String helmet;

    @SerializedName("chestplate")
    private final String chestplate;

    @SerializedName("leggings")
    private final String leggings;

    @SerializedName("boots")
    private final String boots;

    @SerializedName("is_invisible")
    private final boolean isInvisible;

    public PlayerGearInfo(String playerName, String donate, String helmet, String chestplate,
                          String leggings, String boots, boolean isInvisible) {
        this.playerName = playerName;
        this.donate = donate;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.isInvisible = isInvisible;
    }

    public String playerName() { return playerName; }
    public String donate() { return donate; }
    public String helmet() { return helmet; }
    public String chestplate() { return chestplate; }
    public String leggings() { return leggings; }
    public String boots() { return boots; }
    public boolean isInvisible() { return isInvisible; }
}
