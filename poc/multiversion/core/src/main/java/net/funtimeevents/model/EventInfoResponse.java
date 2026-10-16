package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response: detailed info about a single event.
 */
public final class EventInfoResponse {

    @SerializedName("mobs_count")
    private Integer mobsCount;

    public Integer mobsCount() { return mobsCount; }
}
