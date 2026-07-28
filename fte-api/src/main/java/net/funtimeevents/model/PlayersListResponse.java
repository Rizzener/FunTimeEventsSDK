package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response: wrapper for a list of player records.
 */
public final class PlayersListResponse {

    @SerializedName("data")
    private List<PlayerDataResponse> data;

    public List<PlayerDataResponse> data() { return data; }
}
