package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response: wrapper for a list of ban records.
 */
public final class BansListResponse {

    @SerializedName("data")
    private List<BanResponse> data;

    public List<BanResponse> data() { return data; }
}
