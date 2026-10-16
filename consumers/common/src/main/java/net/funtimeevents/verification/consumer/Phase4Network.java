package net.funtimeevents.verification.consumer;

import com.google.gson.*;
import net.funtimeevents.api.*;
import net.funtimeevents.util.GsonHolder;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Test-only backend observer. No SDK sink overrides and no game-data injection. */
public final class Phase4Network {
    private final String base=System.getProperty("fte.phase4.backend");
    private final String mode=System.getProperty("fte.phase4.mode","ws");
    private final HttpClient admin=HttpClient.newHttpClient();
    private final JsonObject fixture=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(
        getClass().getResourceAsStream("/phase4/backend.json")),StandardCharsets.UTF_8)).getAsJsonObject();
    private final CompletableFuture<Void> requests;
    private volatile boolean cachesChecked;
    private int configuredSession;
    public final JsonObject result=new JsonObject();
    public Phase4Network() {
        if("c".equals(System.getProperty("fte.phase4.stage"))) {
            var bootstrap=net.funtimeevents.bootstrap.Bootstrap.getInstance();
            var temporary=new net.funtimeevents.internal.tracker.TrackerRuntime(bootstrap.platform(),net.funtimeevents.spi.OfflineSender.INSTANCE,200);
            bootstrap.install(temporary);
            try{builder().build();throw new AssertionError("Online attach should fail while another owner is attached");}
            catch(IllegalStateException expected){if(bootstrap.runtime()!=temporary)throw new AssertionError("Online rollback detached another owner");}
            finally{bootstrap.detach();}
            result.addProperty("online_initialization_rollback",true);
        }
        builder().build();
        var checks=new ArrayList<CompletableFuture<?>>();
        checks.add(FunTimeEventsAPI.fetchEvents(Map.of("probe","Игрок &?+")).thenAccept(s->equal(JsonParser.parseString(s),fixture.get("events"),"events")));
        checks.add(FunTimeEventsAPI.fetchUserEvents(null).thenAccept(s->equal(JsonParser.parseString(s),fixture.get("user_events"),"user events")));
        checks.add(FunTimeEventsAPI.fetchMines(Map.of()).thenAccept(s->equal(JsonParser.parseString(s),fixture.get("mines"),"mines")));
        checks.add(FunTimeEventsAPI.fetchPlayers(Map.of()).thenAccept(v->equal(GsonHolder.INSTANCE.toJsonTree(v),fixture.get("players"),"players")));
        checks.add(FunTimeEventsAPI.fetchBans(Map.of()).thenAccept(v->equal(GsonHolder.INSTANCE.toJsonTree(v),fixture.get("bans"),"bans")));
        checks.add(FunTimeEventsAPI.fetchCopperDungeon().thenAccept(s->equal(JsonParser.parseString(s),fixture.get("copper_dungeons"),"copper")));
        checks.add(FunTimeEventsAPI.fetchWardenCity().thenAccept(s->equal(JsonParser.parseString(s),fixture.get("warden_cities"),"warden")));
        checks.add(FunTimeEventsAPI.solveCaptcha("cGhhc2U0").thenAccept(v->equal(GsonHolder.INSTANCE.toJsonTree(v),fixture.get("captcha"),"captcha")));
        requests=CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new));
    }
    FteConfig.Builder builder() {
        var b=FunTimeEventsAPI.builder().userAgent("Phase4Consumer/1").apiKey("phase4-local-key").baseUrl(base+"/v1/");
        if(!mode.equals("ws"))b.disableWebSocket();
        if(mode.equals("plain"))b.disableCompression();
        if(Boolean.getBoolean("fte.phase4.flags"))switch(configuredSession) {
            case 1->b.disableBansTracker();
            case 2->b.disableEventCoordinatesTracker();
            case 3->b.disableScanDungeon();
            case 4->b.disableScanMine();
            case 5->b.disableScanTabPlayers();
            case 7->b.disableScanHellMap();
        }
        return b;
    }
    public void reconfigure(int session) {
        FunTimeEventsAPI.shutdown();
        if(!FunTimeEventsAPI.getEvents().isEmpty()||!FunTimeEventsAPI.getUserEvents().isEmpty()||!FunTimeEventsAPI.getMines().isEmpty()
            ||!FunTimeEventsAPI.getCopperDungeons().isEmpty()||!FunTimeEventsAPI.getWardenCities().isEmpty()||FunTimeEventsAPI.getSystemInfo()!=null)
            throw new IllegalStateException("Public caches retained across SDK shutdown");
        configuredSession=session;builder().build();cachesChecked=false;
    }
    public void sameBuilder() { builder().build(); }
    public boolean ready() {
        if(!requests.isDone())return false;
        requests.join();
        if(!cachesChecked) {
            if(FunTimeEventsAPI.getEvents().isEmpty()||FunTimeEventsAPI.getUserEvents().isEmpty()||FunTimeEventsAPI.getMines().isEmpty()
                ||FunTimeEventsAPI.getCopperDungeons().isEmpty()||FunTimeEventsAPI.getWardenCities().isEmpty())return false;
            equal(GsonHolder.INSTANCE.toJsonTree(FunTimeEventsAPI.getEvents()),fixture.getAsJsonObject("events").get("data"),"cached events");
            equal(GsonHolder.INSTANCE.toJsonTree(FunTimeEventsAPI.getUserEvents()),fixture.getAsJsonObject("user_events").get("data"),"cached user events");
            equal(GsonHolder.INSTANCE.toJsonTree(FunTimeEventsAPI.getMines()),fixture.getAsJsonObject("mines").get("data"),"cached mines");
            equal(GsonHolder.INSTANCE.toJsonTree(FunTimeEventsAPI.getCopperDungeons()),fixture.getAsJsonObject("copper_dungeons").get("data"),"cached copper");
            equal(GsonHolder.INSTANCE.toJsonTree(FunTimeEventsAPI.getWardenCities()),fixture.getAsJsonObject("warden_cities").get("data"),"cached warden");
            if(mode.equals("ws"))equal(GsonHolder.INSTANCE.toJsonTree(FunTimeEventsAPI.getSystemInfo()),fixture.get("system_info"),"system info");
            else if(FunTimeEventsAPI.getSystemInfo()!=null)throw new IllegalStateException("REST system info");
            cachesChecked=true;result.addProperty("all_public_requests",true);result.addProperty("all_public_caches",true);
            result.addProperty("mode",mode);System.out.println("[PHASE4] PUBLIC_REQUESTS_CACHES_PASS "+mode);
        }
        return true;
    }
    public CompletableFuture<Void> begin(int session) {
        return admin.sendAsync(HttpRequest.newBuilder(URI.create(base+"/__session?n="+session)).timeout(Duration.ofSeconds(5)).build(),HttpResponse.BodyHandlers.discarding())
            .thenAccept(r->{if(r.statusCode()!=200)throw new IllegalStateException("Backend session control");});
    }
    public CompletableFuture<JsonArray> capture(int session,int expected) {
        return CompletableFuture.supplyAsync(()->{
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(true) {
                try {
                    String raw=admin.send(HttpRequest.newBuilder(URI.create(base+"/__records")).timeout(Duration.ofSeconds(5)).build(),HttpResponse.BodyHandlers.ofString()).body();
                    var rows=new JsonArray();
                    for(var v:JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("records")) {
                        var r=v.getAsJsonObject();if(r.get("session").getAsInt()!=session)continue;
                        String kind=r.get("kind").getAsString();String type=null;JsonElement payload=null;
                        if(kind.equals("post")&&!r.get("type").getAsString().equals("captcha")){type=r.get("type").getAsString();payload=r.get("payload");}
                        if(kind.equals("ws")) {
                            var e=r.getAsJsonObject("envelope");type=Map.of("players","tab","bans","ban","copper_dungeon","copper","warden_city","warden",
                                "hell_map","hell","players_around","mine","coordinates","coordinates").get(e.get("type").getAsString());payload=e.get("body");
                        }
                        if(type!=null) {var row=new JsonObject();row.addProperty("session",session);row.addProperty("tick",-1);
                            row.addProperty("recorded_at_backend",true);row.addProperty("type",type);row.add("payload",payload);rows.add(row);}
                    }
                    if(rows.size()>=expected||System.nanoTime()>=until)return rows;
                    Thread.sleep(20);
                }catch(Exception e){throw new CompletionException(e);}
            }
        });
    }
    public void menuCheck() {if(!ready())throw new IllegalStateException("Backend caches stopped on world leave");}
    public void close() {admin.shutdownNow();}
    private static void equal(JsonElement a,JsonElement b,String label) {if(!Objects.equals(a,b))throw new IllegalStateException(label+" mismatch: "+a+" != "+b);}
}
