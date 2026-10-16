package net.funtimeevents.net;

import com.google.gson.*;
import net.funtimeevents.model.*;
import net.funtimeevents.net.cache.*;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.util.GsonHolder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Same bytecode runs against release transport and the migrated transport. */
public final class NetworkParityCheck {
    private static final com.google.gson.Gson G=GsonHolder.INSTANCE;
    private static final JsonObject GOLD=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(
        NetworkParityCheck.class.getResourceAsStream("/phase4/backend.json")),StandardCharsets.UTF_8)).getAsJsonObject();
    private static void equal(Object value,String name) {
        JsonElement actual=value instanceof String s?JsonParser.parseString(s):G.toJsonTree(value);
        if(!actual.equals(GOLD.get(name)))throw new AssertionError(name+": "+actual);
    }
    public static void main(String[] args)throws Exception {
        String base=args[0],label=args[1];
        System.out.println("[PARITY] source="+ApiClient.class.getProtectionDomain().getCodeSource().getLocation()+" java="+Runtime.version());
        ApiClient rest=new ApiClient(base+"/v1/","phase4-local-key","Parity/"+label,true);
        equal(rest.getEvents(Map.of("probe","Игрок &?+")).get(20,TimeUnit.SECONDS),"events");
        equal(rest.getEvents(Map.of("event_type","user")).get(20,TimeUnit.SECONDS),"user_events");
        equal(rest.getMines(Map.of()).get(20,TimeUnit.SECONDS),"mines");
        equal(rest.getPlayers(Map.of()).get(20,TimeUnit.SECONDS),"players");
        equal(rest.getBans(Map.of()).get(20,TimeUnit.SECONDS),"bans");
        equal(rest.getCopperDungeon().get(20,TimeUnit.SECONDS),"copper_dungeons");
        equal(rest.getWardenCity().get(20,TimeUnit.SECONDS),"warden_cities");
        equal(rest.solveCaptcha("cGhhc2U0").get(20,TimeUnit.SECONDS),"captcha");
        var error=new ApiClient(base+"/error/v1","phase4-local-key","ParityError/"+label,true);
        if(!JsonParser.parseString(error.getEvents(Map.of()).get(5,TimeUnit.SECONDS)).getAsJsonObject().get("error").getAsString().equals("fixture failure"))throw new AssertionError("HTTP error body semantics");
        if(error.getPlayers(Map.of()).get(5,TimeUnit.SECONDS).data()!=null)throw new AssertionError("typed error semantics");
        var captchaError=error.solveCaptcha("cGhhc2U0").get(5,TimeUnit.SECONDS);
        if(captchaError.solved()||captchaError.text()!=null)throw new AssertionError("captcha HTTP error semantics");
        var malformed=new ApiClient(base+"/malformed/v1","phase4-local-key","ParityMalformed/"+label,true);
        expectMalformed(malformed.getPlayers(Map.of()));expectMalformed(malformed.solveCaptcha("AA=="));
        sendAll(rest);Thread.sleep(500);
        ApiClient plain=new ApiClient(base+"/v1","phase4-local-key","ParityPlain/"+label,false);
        sendAll(plain);equal(plain.solveCaptcha("cGhhc2U0").get(20,TimeUnit.SECONDS),"captcha");
        var cache=new RelayCache();RelayClient relay=new RelayClient(base.replace("https://","wss://").replace("http://","ws://")+"/v1/relay",
            "phase4-local-key","Parity/"+label,true);relay.setCache(cache);relay.connect();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while((cache.getEvents().isEmpty()||cache.getUserEvents().isEmpty()||cache.getMines().isEmpty()
            ||cache.getCopperDungeons().isEmpty()||cache.getWardenCities().isEmpty()||cache.getSystemInfo()==null)&&System.nanoTime()<until)Thread.sleep(20);
        if(!G.toJsonTree(cache.getEvents()).equals(GOLD.getAsJsonObject("events").get("data")))throw new AssertionError("relay snapshot");
        if(cache.getUserEvents().isEmpty()||cache.getMines().isEmpty()||cache.getCopperDungeons().isEmpty()||cache.getWardenCities().isEmpty())throw new AssertionError("relay lists");
        if(cache.getSystemInfo().clientsConnected()!=2)throw new AssertionError("system info");
        sendAll(relay);Thread.sleep(500);relay.disconnect();
        var sse=new SseCache<EventResponse>("Parity-SSE",EventResponse.class,e->e.serverType()+e.serverId());
        sse.start(rest.streamEvents());until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(sse.getData().isEmpty()&&System.nanoTime()<until)Thread.sleep(20);
        if(!G.toJsonTree(sse.getData()).equals(GOLD.getAsJsonObject("events").get("data")))throw new AssertionError("SSE");sse.stop();
        var stale=new RelayCache();var snapshot=new JsonObject();snapshot.addProperty("type","snapshot");snapshot.add("events",GOLD.getAsJsonObject("events").get("data"));
        stale.updateFromSnapshot(snapshot.toString());if(stale.getEvents().isEmpty())throw new AssertionError("new cache stale");
        Thread.sleep(5200);if(!stale.getEvents().isEmpty())throw new AssertionError("cache TTL");
        var timeout=new ApiClient(base+"/timeout/v1","phase4-local-key","ParityTimeout/"+label,false);
        long at=System.nanoTime();String timed=timeout.getEvents(Map.of()).get(20,TimeUnit.SECONDS);
        long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-at);
        if(timed!=null||ms<14000||ms>19000)throw new AssertionError("HTTP timeout "+ms);
        for(Object client:List.of(rest,plain,error,malformed,timeout))if(client instanceof AutoCloseable closeable)closeable.close();
        System.out.println("[PARITY] PASS GET POST7 gzip plain WS-auth text snapshots SSE TTL HTTP-timeout="+ms);
    }
    private static void expectMalformed(CompletableFuture<?> request)throws Exception {
        try{request.get(5,TimeUnit.SECONDS);throw new AssertionError("malformed JSON must fail the typed future");}
        catch(ExecutionException expected){if(!(expected.getCause() instanceof com.google.gson.JsonParseException))throw expected;}
    }
    private static void sendAll(PayloadSender sender)throws Exception {
        JsonArray sessions=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(NetworkParityCheck.class.getResourceAsStream("/phase3/tracker-sessions.json")),StandardCharsets.UTF_8)).getAsJsonArray();
        Map<String,JsonElement> payloads=new LinkedHashMap<>();for(var row:sessions)for(var e:row.getAsJsonObject().getAsJsonObject("expected").entrySet())
            if(!e.getValue().getAsJsonArray().isEmpty())payloads.putIfAbsent(e.getKey(),e.getValue().getAsJsonArray().get(0));
        for(var e:payloads.entrySet()) {
            switch(e.getKey()) {
                case "tab"->sender.sendTabPlayers(G.fromJson(e.getValue(),TabPlayersPayload.class));
                case "ban"->sender.sendBan(G.fromJson(e.getValue(),BanPayload.class));
                case "copper"->sender.sendCopperDungeon(G.fromJson(e.getValue(),DungeonPayload.class));
                case "warden"->sender.sendWardenCity(G.fromJson(e.getValue(),DungeonPayload.class));
                case "mine"->sender.sendMinePlayers(G.fromJson(e.getValue(),MinePlayersAroundPayload.class));
                case "hell"->sender.sendHellMap(G.fromJson(e.getValue(),HellMapPayload.class));
                case "coordinates"->sender.sendEventCoordinates(G.fromJson(e.getValue(),EventCoordinatesPayload.class));
            }
            Thread.sleep(100);
        }
    }
}
