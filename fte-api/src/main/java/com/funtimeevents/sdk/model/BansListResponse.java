package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class BansListResponse {

    @SerializedName("data")
    private List<BanResponse> data;

    public List<BanResponse> data() { return data; }
}
