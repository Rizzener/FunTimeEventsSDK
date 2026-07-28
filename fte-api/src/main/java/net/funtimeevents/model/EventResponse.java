package net.funtimeevents.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response: an active event with time left and optional coordinates.
 */
public final class EventResponse {

    @SerializedName("server_id")
    private int serverId;

    @SerializedName("server_type")
    private String serverType;

    @SerializedName("name")
    private String name;

    @SerializedName("status")
    private String status;

    @SerializedName("time_left")
    private int timeLeft;

    @SerializedName("level")
    private String level;

    @SerializedName("level_updated_at")
    private String levelUpdatedAt;

    @SerializedName("coordinates")
    private EventCoordinates coordinates;

    @SerializedName("message")
    private String message;

    @SerializedName("event_info")
    private EventInfoResponse eventInfo;

    @SerializedName("updated_at")
    private String updatedAt;

    public int serverId() { return serverId; }
    public String serverType() { return serverType; }
    public String name() { return name; }
    public String status() { return status; }
    public int timeLeft() { return timeLeft; }
    public String level() { return level; }
    public String levelUpdatedAt() { return levelUpdatedAt; }
    public EventCoordinates coordinates() { return coordinates; }
    public String message() { return message; }
    public EventInfoResponse eventInfo() { return eventInfo; }
    public String updatedAt() { return updatedAt; }
}
