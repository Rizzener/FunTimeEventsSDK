package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response: a relay snapshot containing events, mines, dungeons, and system info.
 */
public final class Snapshot {

    @SerializedName("type")
    private String type;

    @SerializedName("events")
    private List<EventResponse> events;

    @SerializedName("user_events")
    private List<EventResponse> userEvents;

    @SerializedName("mines")
    private List<MineResponse> mines;

    @SerializedName("copper_dungeons")
    private List<LootAreaResponse> copperDungeons;

    @SerializedName("warden_cities")
    private List<LootAreaResponse> wardenCities;

    @SerializedName("system_info")
    private SystemInfo systemInfo;

    @SerializedName("ts")
    private double ts;

    public String type() { return type; }
    public List<EventResponse> events() { return events; }
    public List<EventResponse> userEvents() { return userEvents; }
    public List<MineResponse> mines() { return mines; }
    public List<LootAreaResponse> copperDungeons() { return copperDungeons; }
    public List<LootAreaResponse> wardenCities() { return wardenCities; }
    public SystemInfo systemInfo() { return systemInfo; }
    public double ts() { return ts; }
}
