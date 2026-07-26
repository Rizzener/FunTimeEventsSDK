package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

public final class SpawnCoordinates {

    @SerializedName("x")
    private final Integer x;

    @SerializedName("y")
    private final Integer y;

    @SerializedName("z")
    private final Integer z;

    public SpawnCoordinates(Integer x, Integer y, Integer z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Integer x() { return x; }
    public Integer y() { return y; }
    public Integer z() { return z; }
}
