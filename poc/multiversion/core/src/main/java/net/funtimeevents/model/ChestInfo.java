package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Immutable: a single chest found in a dungeon zone (position, time left).
 */
public final class ChestInfo {

    @SerializedName("x")
    private final int x;

    @SerializedName("y")
    private final int y;

    @SerializedName("z")
    private final int z;

    @SerializedName("time_left")
    private final int timeLeft;

    public ChestInfo(int x, int y, int z, int timeLeft) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.timeLeft = timeLeft;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int timeLeft() { return timeLeft; }
}
