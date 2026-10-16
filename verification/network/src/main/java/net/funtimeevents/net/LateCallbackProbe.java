package net.funtimeevents.net;
import com.google.gson.*;
import net.funtimeevents.model.BanPayload;
import net.funtimeevents.net.cache.RelayCache;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** Real old connection auth-timeout followed by its late 4001 while new is alive. */
public final class LateCallbackProbe {
    public static void main(String[] args)throws Exception {
        String base=args[0];var cache=new RelayCache();
        var relay=new RelayClient(base.replace("https://","wss://")+"/late/v1/relay","phase4-local-key","LateProbe",false);
        relay.setCache(cache);relay.connect();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        while(cache.getEvents().isEmpty()&&System.nanoTime()<until)Thread.sleep(20);
        if(cache.getEvents().isEmpty())throw new AssertionError("second connection never authenticated");
        Thread.sleep(2200);relay.sendBan(new BanPayload(401,"anarchy","AfterLate","fixture","forever"));Thread.sleep(700);
        String raw=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base+"/__records")).build(),HttpResponse.BodyHandlers.ofString()).body();
        boolean delivered=false,late=false;
        for(var v:JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("records")) {
            var r=v.getAsJsonObject();if(r.get("kind").getAsString().equals("late_old_close"))late=true;
            if(r.get("kind").getAsString().equals("ws")&&r.getAsJsonObject("envelope").get("type").getAsString().equals("bans"))delivered=true;
        }
        var result=new JsonObject();result.addProperty("late_old_close_sent",late);result.addProperty("new_connection_delivered_after_late_old_rejection",delivered);
        result.addProperty("source",RelayClient.class.getProtectionDomain().getCodeSource().getLocation().toString());
        Files.writeString(Path.of(args[1]),result.toString());System.out.println("[LATE-CALLBACK-PROBE] "+result);relay.disconnect();
    }
}
