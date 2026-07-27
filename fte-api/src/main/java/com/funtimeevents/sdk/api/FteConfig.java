package com.funtimeevents.sdk.api;

public final class FteConfig {

    public enum LogLevel { OFF, ERROR, WARN, INFO, DEBUG }

    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final LogLevel logLevel;
    private final boolean tabPlayersEnabled;
    private final boolean bansEnabled;
    private final boolean dungeonEnabled;
    private final boolean hellMapEnabled;
    private final boolean mineEnabled;
    private final boolean spawnEnabled;
    private final int tickIntervalTicks;
    private final boolean offlineMode;

    FteConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.apiKey;
        this.userAgent = builder.userAgent;
        this.logLevel = builder.logLevel;
        this.tabPlayersEnabled = builder.tabPlayersEnabled;
        this.bansEnabled = builder.bansEnabled;
        this.dungeonEnabled = builder.dungeonEnabled;
        this.hellMapEnabled = builder.hellMapEnabled;
        this.mineEnabled = builder.mineEnabled;
        this.spawnEnabled = builder.spawnEnabled;
        this.tickIntervalTicks = builder.tickIntervalTicks;
        this.offlineMode = builder.offlineMode;
    }

    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String userAgent() { return userAgent; }
    public LogLevel logLevel() { return logLevel; }
    public boolean tabPlayersEnabled() { return tabPlayersEnabled; }
    public boolean bansEnabled() { return bansEnabled; }
    public boolean dungeonEnabled() { return dungeonEnabled; }
    public boolean hellMapEnabled() { return hellMapEnabled; }
    public boolean mineEnabled() { return mineEnabled; }
    public boolean spawnEnabled() { return spawnEnabled; }
    public int tickIntervalTicks() { return tickIntervalTicks; }
    public boolean offlineMode() { return offlineMode; }

    public static final class Builder {
        String baseUrl = "https://api.funtimeevents.su/v1";
        String apiKey;
        String userAgent;
        LogLevel logLevel = LogLevel.INFO;
        boolean tabPlayersEnabled = true;
        boolean bansEnabled = true;
        boolean dungeonEnabled = true;
        boolean hellMapEnabled = true;
        boolean mineEnabled = true;
        boolean spawnEnabled = true;
        int tickIntervalTicks = 200;
        boolean offlineMode;

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder disableTabPlayers() {
            this.tabPlayersEnabled = false;
            return this;
        }

        public Builder disableBans() {
            this.bansEnabled = false;
            return this;
        }

        public Builder disableDungeon() {
            this.dungeonEnabled = false;
            return this;
        }

        public Builder disableHellMap() {
            this.hellMapEnabled = false;
            return this;
        }

        public Builder disableMine() {
            this.mineEnabled = false;
            return this;
        }

        public Builder disableSpawn() {
            this.spawnEnabled = false;
            return this;
        }

        public Builder tickIntervalSeconds(int seconds) {
            this.tickIntervalTicks = Math.max(20, seconds * 20);
            return this;
        }

        public Builder offlineMode() {
            this.offlineMode = true;
            return this;
        }

        public FunTimeEventsAPI build() {
            return FunTimeEventsAPI.create(this);
        }
    }
}
