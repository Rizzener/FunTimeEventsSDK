package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Immutable: x/y/z coordinates of an event.
 */
public final class EventCoordinates {

    @SerializedName("x")
    private final Integer x;

    @SerializedName("y")
    private final Integer y;

    @SerializedName("z")
    private final Integer z;

    public EventCoordinates(Integer x, Integer y, Integer z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Integer x() { return x; }
    public Integer y() { return y; }
    public Integer z() { return z; }
}
