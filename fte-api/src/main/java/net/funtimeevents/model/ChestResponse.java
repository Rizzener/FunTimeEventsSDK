package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

public final class ChestResponse {

    @SerializedName("x")
    private int x;

    @SerializedName("y")
    private int y;

    @SerializedName("z")
    private int z;

    @SerializedName("time_left")
    private int timeLeft;

    @SerializedName("created_at")
    private String createdAt;

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int timeLeft() { return timeLeft; }
    public String createdAt() { return createdAt; }
}
