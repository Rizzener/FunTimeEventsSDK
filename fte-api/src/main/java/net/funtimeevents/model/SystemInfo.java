package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response: backend system statistics (event/mine/dungeon counts, connected clients).
 */
public final class SystemInfo {

    @SerializedName("events")
    private int events;

    @SerializedName("mines")
    private int mines;

    @SerializedName("copper_dungeons")
    private int copperDungeons;

    @SerializedName("warden_cities")
    private int wardenCities;

    @SerializedName("clients_connected")
    private int clientsConnected;

    @SerializedName("tracked_anarchies")
    private int trackedAnarchies;

    public int events() { return events; }
    public int mines() { return mines; }
    public int copperDungeons() { return copperDungeons; }
    public int wardenCities() { return wardenCities; }
    public int clientsConnected() { return clientsConnected; }
    public int trackedAnarchies() { return trackedAnarchies; }
}
