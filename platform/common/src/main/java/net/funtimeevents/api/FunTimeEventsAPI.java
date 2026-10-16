package net.funtimeevents.api;

import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.bootstrap.Bootstrap;
import net.funtimeevents.model.*;
import net.funtimeevents.net.ApiClient;
import net.funtimeevents.net.RelayClient;
import net.funtimeevents.net.cache.RelayCache;
import net.funtimeevents.net.cache.SseCache;
import net.funtimeevents.internal.tracker.TrackerRuntime;
import net.funtimeevents.spi.OfflineSender;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.spi.SessionFence;
import net.funtimeevents.util.FteLogger;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Existing SDK facade. One fully initialized runtime is published atomically. */
public final class FunTimeEventsAPI {
    private static volatile FunTimeEventsAPI instance;
    private final FteConfig config;
    private final SessionFence gameSession=new SessionFence();
    private volatile ApiClient restClient;
    private volatile RelayClient wsClient;
    private volatile RelayCache relayCache;
    private volatile SseCache<EventResponse> eventCache, userEventCache;
    private volatile SseCache<MineResponse> mineCache;
    private volatile SseCache<LootAreaResponse> copperCache, wardenCache;
    private volatile TrackerRuntime trackers;
    private FunTimeEventsAPI(FteConfig config) { this.config=config; }
    public static FteConfig.Builder builder() { return new FteConfig.Builder(); }
    public static String getVersion() {
        return FabricLoader.getInstance().getModContainer("fte-api")
            .map(c->c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }
    static synchronized FunTimeEventsAPI create(FteConfig.Builder builder) {
        FteConfig config=new FteConfig(builder);
        validate(config);
        if(instance!=null) {
            if(!sameConfig(instance.config,config))
                throw new IllegalStateException("SDK already initialized with a different configuration; sharing credentials/configuration implicitly is forbidden");
            return instance;
        }
        Bootstrap.requireClientThread();
        var api=new FunTimeEventsAPI(config);
        var previousLogLevel=FteLogger.getLevel();FteLogger.setLevel(config.logLevel());
        try {
            api.initialize(); instance=api;
            FteLogger.setLevel(config.logLevel());
            FteLogger.info(FteLogger.CORE,"SDK initialized mode="+(config.offlineMode()?"offline":config.wsMode()?"wss":"rest"));
            return api;
        } catch(RuntimeException | Error error) {
            if(Bootstrap.getInstance().runtime()==api.trackers && api.trackers!=null)Bootstrap.getInstance().detach();
            api.closeResources();FteLogger.setLevel(previousLogLevel);throw error;
        }
    }
    private static void validate(FteConfig c) {
        if(c.userAgent()==null||c.userAgent().isBlank())throw new IllegalArgumentException("userAgent is required");
        if(!c.offlineMode()&&(c.apiKey()==null||c.apiKey().isBlank()))throw new IllegalArgumentException("apiKey is required");
        if(c.logLevel()==null)throw new IllegalArgumentException("logLevel is required");
        if(c.userAgent().chars().anyMatch(ch->ch==0||ch=='\r'||ch=='\n') || (c.apiKey()!=null&&c.apiKey().chars().anyMatch(ch->ch==0||ch=='\r'||ch=='\n')))
            throw new IllegalArgumentException("Invalid header characters in SDK configuration");
        try {
            var uri=URI.create(c.baseUrl());
            if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)
                throw new IllegalArgumentException();
        } catch(RuntimeException e) {throw new IllegalArgumentException("baseUrl must be an HTTP(S) API base URL without credentials/query/fragment");}
    }
    private static boolean sameConfig(FteConfig a,FteConfig b) {
        return Objects.equals(a.baseUrl(),b.baseUrl())&&Objects.equals(a.apiKey(),b.apiKey())&&Objects.equals(a.userAgent(),b.userAgent())
            &&a.logLevel()==b.logLevel()&&a.tabPlayersEnabled()==b.tabPlayersEnabled()&&a.bansEnabled()==b.bansEnabled()
            &&a.dungeonEnabled()==b.dungeonEnabled()&&a.hellMapEnabled()==b.hellMapEnabled()&&a.mineEnabled()==b.mineEnabled()
            &&a.coordinatesEnabled()==b.coordinatesEnabled()&&a.tickIntervalSeconds()==b.tickIntervalSeconds()
            &&a.offlineMode()==b.offlineMode()&&a.wsMode()==b.wsMode()&&a.compression()==b.compression();
    }
    private void initialize() {
        PayloadSender sender=OfflineSender.INSTANCE;
        if(!config.offlineMode()) {
            restClient=new ApiClient(config.baseUrl(),config.apiKey(),config.userAgent(),config.compression(),gameSession);
            if(config.wsMode()) {
                String raw=config.baseUrl();if(raw.endsWith("/"))raw=raw.substring(0,raw.length()-1);
                wsClient=new RelayClient(raw.replace("https://","wss://").replace("http://","ws://")+"/relay",config.apiKey(),config.userAgent(),config.compression(),gameSession);
                relayCache=new RelayCache();wsClient.setCache(relayCache);wsClient.connect();sender=wsClient;
            } else {
                sender=restClient;
                eventCache=new SseCache<>("FTE-EventStream",EventResponse.class,FunTimeEventsAPI::systemEventKey);eventCache.startWith(restClient::streamEvents);
                userEventCache=new SseCache<>("FTE-UserEventStream",EventResponse.class,FunTimeEventsAPI::userEventKey);userEventCache.startWith(restClient::streamUserEvents);
                mineCache=new SseCache<>("FTE-MineStream",MineResponse.class,m->m.serverId()+"_"+m.rarity());mineCache.startWith(restClient::streamMines);
                copperCache=new SseCache<>("FTE-CopperStream",LootAreaResponse.class,l->String.valueOf(l.serverId()));copperCache.startWith(restClient::streamCopperDungeons);
                wardenCache=new SseCache<>("FTE-WardenStream",LootAreaResponse.class,l->String.valueOf(l.serverId()));wardenCache.startWith(restClient::streamWardenCities);
            }
        }
        trackers=new TrackerRuntime(Bootstrap.getInstance().platform(),sender,config.tickIntervalSeconds()*20,
            config.bansEnabled(),config.dungeonEnabled(),config.hellMapEnabled(),config.tabPlayersEnabled(),config.mineEnabled(),config.coordinatesEnabled(),gameSession);
        Bootstrap.getInstance().install(trackers);
    }
    /** Stops the SDK runtime; build/shutdown belong to the Minecraft client thread. */
    public static synchronized void shutdown() {
        if(instance==null)return;
        Bootstrap.requireClientThread();var old=instance;instance=null;
        try {Bootstrap.getInstance().detach();}finally{old.closeResources();}
    }
    private void closeResources() {
        if(eventCache!=null)eventCache.stop();if(userEventCache!=null)userEventCache.stop();
        if(mineCache!=null)mineCache.stop();if(copperCache!=null)copperCache.stop();if(wardenCache!=null)wardenCache.stop();
        if(wsClient!=null)wsClient.disconnect();if(restClient!=null)restClient.close();
    }
    public static CompletableFuture<String> fetchEvents(Map<String,String> params) {
        var r=instance;return r!=null&&r.restClient!=null?r.restClient.getEvents(params):CompletableFuture.completedFuture(null);
    }
    public static CompletableFuture<String> fetchUserEvents(Map<String,String> params) {
        var r=instance;if(r==null||r.restClient==null)return CompletableFuture.completedFuture(null);
        if(params==null||!params.containsKey("event_type")) {var merged=new HashMap<String,String>();if(params!=null)merged.putAll(params);merged.put("event_type","user");params=merged;}
        return r.restClient.getEvents(params);
    }
    public static CompletableFuture<String> fetchMines(Map<String,String> params) {
        var r=instance;return r!=null&&r.restClient!=null?r.restClient.getMines(params):CompletableFuture.completedFuture(null);
    }
    public static CompletableFuture<PlayersListResponse> fetchPlayers(Map<String,String> params) {
        var r=instance;return r!=null&&r.restClient!=null?r.restClient.getPlayers(params):CompletableFuture.completedFuture(null);
    }
    public static CompletableFuture<BansListResponse> fetchBans(Map<String,String> params) {
        var r=instance;return r!=null&&r.restClient!=null?r.restClient.getBans(params):CompletableFuture.completedFuture(null);
    }
    public static CompletableFuture<String> fetchCopperDungeon() {
        var r=instance;return r!=null&&r.restClient!=null?r.restClient.getCopperDungeon():CompletableFuture.completedFuture(null);
    }
    public static CompletableFuture<String> fetchWardenCity() {
        var r=instance;return r!=null&&r.restClient!=null?r.restClient.getWardenCity():CompletableFuture.completedFuture(null);
    }
    public static List<EventResponse> getEvents() {
        var r=instance;return r==null?List.of():r.relayCache!=null?r.relayCache.getEvents():r.eventCache!=null?r.eventCache.getData():List.of();
    }
    public static List<EventResponse> getUserEvents() {
        var r=instance;return r==null?List.of():r.relayCache!=null?r.relayCache.getUserEvents():r.userEventCache!=null?r.userEventCache.getData():List.of();
    }
    public static List<MineResponse> getMines() {
        var r=instance;return r==null?List.of():r.relayCache!=null?r.relayCache.getMines():r.mineCache!=null?r.mineCache.getData():List.of();
    }
    public static List<LootAreaResponse> getCopperDungeons() {
        var r=instance;return r==null?List.of():r.relayCache!=null?r.relayCache.getCopperDungeons():r.copperCache!=null?r.copperCache.getData():List.of();
    }
    public static List<LootAreaResponse> getWardenCities() {
        var r=instance;return r==null?List.of():r.relayCache!=null?r.relayCache.getWardenCities():r.wardenCache!=null?r.wardenCache.getData():List.of();
    }
    public static SystemInfo getSystemInfo(){var r=instance;return r!=null&&r.relayCache!=null?r.relayCache.getSystemInfo():null;}
    public static CompletableFuture<CaptchaResponse> solveCaptcha(String base64) {
        var r=instance;return r!=null&&r.restClient!=null?r.restClient.solveCaptcha(base64):CompletableFuture.completedFuture(null);
    }
    private static String systemEventKey(EventResponse e){return(e.serverType()==null?"":e.serverType())+e.serverId();}
    private static String userEventKey(EventResponse e) {
        String key=systemEventKey(e)+":"+(e.eventId()!=null?e.eventId():e.name()!=null?e.name():"?");
        if(e.coordinates()!=null)key+=":"+e.coordinates().x()+":"+e.coordinates().y()+":"+e.coordinates().z();return key;
    }
}
