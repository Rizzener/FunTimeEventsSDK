package net.funtimeevents.api;

/**
 * Immutable SDK configuration, created via {@link Builder}.
 *
 * <p>All values are read-only. Use {@link FunTimeEventsAPI#builder()}
 * to obtain a {@code Builder} instance.
 */
public final class FteConfig {

    /**
     * Log severity for the SDK.
     *
     * <p>Each level includes all levels with higher severity.
     * {@code DEBUG} shows everything, {@code OFF} silences all output.
     */
    public enum LogLevel {
        OFF(0), ERROR(1), WARN(2), INFO(3), DEBUG(4);

        /** Numeric severity — higher means more verbose. */
        public final int severity;

        LogLevel(int severity) {
            this.severity = severity;
        }
    }

    private final String baseUrl;
    private final String apiKey;
    private final String userAgent;
    private final LogLevel logLevel;
    private final boolean tabPlayersEnabled;
    private final boolean bansEnabled;
    private final boolean dungeonEnabled;
    private final boolean hellMapEnabled;
    private final boolean mineEnabled;
    private final boolean coordinatesEnabled;
    private final int tickIntervalTicks;
    private final boolean offlineMode;
    private final boolean wsMode;
    private final boolean compression;

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
        this.coordinatesEnabled = builder.coordinatesEnabled;
        this.tickIntervalTicks = builder.tickIntervalTicks;
        this.offlineMode = builder.offlineMode;
        this.wsMode = builder.wsMode;
        this.compression = builder.compression;
    }

    /** Backend base URL, including the API version prefix ({@code /v1}). */
    public String baseUrl() { return baseUrl; }
    /** API key for backend authentication. */
    public String apiKey() { return apiKey; }
    /** User-Agent string sent with every HTTP request. */
    public String userAgent() { return userAgent; }
    /** Current log level. */
    public LogLevel logLevel() { return logLevel; }
    /** Whether TAB player scanning is enabled. */
    public boolean tabPlayersEnabled() { return tabPlayersEnabled; }
    /** Whether ban tracking is enabled. */
    public boolean bansEnabled() { return bansEnabled; }
    /** Whether dungeon scanning is enabled. */
    public boolean dungeonEnabled() { return dungeonEnabled; }
    /** Whether Hell Map (boss bar) tracking is enabled. */
    public boolean hellMapEnabled() { return hellMapEnabled; }
    /** Whether auto-mine player counting is enabled. */
    public boolean mineEnabled() { return mineEnabled; }
    /** Whether event coordinate tracking is enabled. */
    public boolean coordinatesEnabled() { return coordinatesEnabled; }
    /** Scheduler interval in seconds (derived from ticks). */
    public int tickIntervalSeconds() { return tickIntervalTicks / 20; }
    /** Whether the SDK runs in offline mode (no network). */
    public boolean offlineMode() { return offlineMode; }
    /** Whether WebSocket relay is used (vs. REST-only). */
    public boolean wsMode() { return wsMode; }
    /** Whether HTTP request bodies are gzip-compressed. */
    public boolean compression() { return compression; }

    /**
     * Fluent builder for {@link FteConfig}.
     *
     * <p>All methods return {@code this} for chaining.
     * Call {@link #build()} to finalize and initialise the SDK.
     */
    public static final class Builder {
        String baseUrl = "https://api.funtimeevents.su/v1/";
        String apiKey;
        String userAgent;
        LogLevel logLevel = LogLevel.INFO;
        boolean tabPlayersEnabled = true;
        boolean bansEnabled = true;
        boolean dungeonEnabled = true;
        boolean hellMapEnabled = true;
        boolean mineEnabled = true;
        boolean coordinatesEnabled = true;
        int tickIntervalTicks = 200;
        boolean offlineMode;
        boolean wsMode = true;
        boolean compression = true;

        /**
         * Sets the {@code User-Agent} header for all backend requests.
         * <strong>Required.</strong>
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent; return this;
        }
        /**
         * Sets the API key for backend authentication.
         * Optional — if omitted, requests are sent without X-API-Key
         * (e.g. when a proxy adds it).
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey; return this;
        }
        /**
         * Overrides the backend base URL.
         * Default is {@code https://api.funtimeevents.su/v1/}.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl; return this;
        }
        /**
         * Sets the log level.
         * Default is {@link LogLevel#INFO}.
         */
        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel; return this;
        }
        /** Disables TAB player scanning. */
        public Builder disableScanTabPlayers() {
            this.tabPlayersEnabled = false; return this;
        }
        /** Disables ban tracking. */
        public Builder disableBansTracker() {
            this.bansEnabled = false; return this;
        }
        /** Disables dungeon scanning (Copper + Warden City). */
        public Builder disableScanDungeon() {
            this.dungeonEnabled = false; return this;
        }
        /** Disables Hell Map (boss bar) tracking. */
        public Builder disableScanHellMap() {
            this.hellMapEnabled = false; return this;
        }
        /** Disables auto-mine player counting. */
        public Builder disableScanMine() {
            this.mineEnabled = false; return this;
        }
        /** Disables event coordinate tracking. */
        public Builder disableEventCoordinatesTracker() {
            this.coordinatesEnabled = false; return this;
        }
        /**
         * Sets the interval between tracker ticks.
         * Default is 10 seconds (200 ticks). Minimum is 1 second (20 ticks).
         */
        public Builder tickIntervalSeconds(int seconds) {
            this.tickIntervalTicks = Math.max(20, seconds * 20); return this;
        }
        /**
         * Enables offline mode — no network requests are made.
         * Trackers still collect data locally.
         */
        public Builder offlineMode() {
            this.offlineMode = true; return this;
        }
        /**
         * Disables the WebSocket relay.
         * When disabled, all POST data is sent via REST (HTTP).
         */
        public Builder disableWebSocket() {
            this.wsMode = false; return this;
        }
        /**
         * Disables gzip compression for HTTP request bodies.
         * WebSocket messages are never compressed regardless of this setting.
         */
        public Builder disableCompression() {
            this.compression = false; return this;
        }
        /**
         * Builds the configuration and initialises the SDK.
         * Must be called once — subsequent calls are silently ignored.
         */
        public FunTimeEventsAPI build() {
            return FunTimeEventsAPI.create(this);
        }
    }
}
