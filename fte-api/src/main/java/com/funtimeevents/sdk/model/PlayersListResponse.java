package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class PlayersListResponse {

    @SerializedName("data")
    private List<PlayerDataResponse> data;

    public List<PlayerDataResponse> data() { return data; }
}
