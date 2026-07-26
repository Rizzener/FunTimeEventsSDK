package com.funtimeevents.sdk.model;

import com.google.gson.annotations.SerializedName;

public final class SpawnEventPayload {

    @SerializedName("server_id")
    private final int serverId;

    @SerializedName("server_type")
    private final String serverType;

    @SerializedName("event")
    private final String event;

    @SerializedName("level")
    private final String level;

    @SerializedName("coordinates")
    private final SpawnCoordinates coordinates;

    public SpawnEventPayload(int serverId, String serverType, String event, String level, SpawnCoordinates coordinates) {
        this.serverId = serverId;
        this.serverType = serverType;
        this.event = event;
        this.level = level;
        this.coordinates = coordinates;
    }

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public String event() { return event; }
    public String level() { return level; }
    public SpawnCoordinates coordinates() { return coordinates; }
}
