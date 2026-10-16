package net.funtimeevents.net;
import com.google.gson.*;
import net.funtimeevents.model.*;
import net.funtimeevents.net.cache.*;
import net.funtimeevents.util.GsonHolder;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.SSLSession;

/** Observations of known faults. False means not reproduced, not an invented failure. */
public final class NetworkFaultProbe {
    static final class ObservedList extends AbstractList<ObservedPlayer> {
        final AtomicReference<String> thread;final AtomicInteger calls;
        ObservedList(AtomicReference<String> thread,AtomicInteger calls){this.thread=thread;this.calls=calls;}
        public int size(){return 1;}
        public ObservedPlayer get(int i){return new ObservedPlayer("Probe","⚡TEST","<ISO8601>");}
        public Iterator<ObservedPlayer> iterator(){thread.set(Thread.currentThread().getName());calls.incrementAndGet();return super.iterator();}
    }
    static final class BlockingInput extends InputStream {
        volatile boolean closed;final AtomicInteger closes=new AtomicInteger();final CountDownLatch reading=new CountDownLatch(1);
        public synchronized int read(){reading.countDown();while(!closed)try{wait();}catch(InterruptedException e){/* Emulate a non-interruptible stream. */}return -1;}
        public synchronized void close(){closed=true;closes.incrementAndGet();notifyAll();}
    }
    record Response(InputStream body) implements HttpResponse<InputStream> {
        public int statusCode(){return 200;}public HttpRequest request(){return HttpRequest.newBuilder(URI.create("http://localhost/")).build();}
        public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}
        public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}
        public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return URI.create("http://localhost/");}
        public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
    }
    public static void main(String[] args)throws Exception {
        String base=args[0],label=args[1];var result=new JsonObject();
        result.addProperty("source",ApiClient.class.getProtectionDomain().getCodeSource().getLocation().toString());
        var calls=new AtomicInteger();var thread=new AtomicReference<String>();
        var rest=new ApiClient(base+"/v1","phase4-local-key","Fault/"+label,true);
        String original=Thread.currentThread().getName();Thread.currentThread().setName("Render thread");
        rest.sendTabPlayers(new TabPlayersPayload(401,"anarchy",new ObservedList(thread,calls)));
        Thread.currentThread().setName(original);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(thread.get()==null&&System.nanoTime()<until)Thread.sleep(10);
        result.addProperty("HTTP_serialization_on_caller",Objects.equals(thread.get(),"Render thread"));result.addProperty("HTTP_serialization_thread",thread.get());
        var pendingCalls=new AtomicInteger();var pendingThread=new AtomicReference<String>();
        var pending=new RelayClient(base.replace("https://","wss://")+"/v1/relay","phase4-local-key","Fault/"+label,false);
        for(int i=0;i<5000;i++)pending.sendTabPlayers(new TabPlayersPayload(401,"anarchy",new ObservedList(pendingThread,pendingCalls)));
        Thread.sleep(1000);result.addProperty("unconnected_snapshot_serializations_5000_offers",pendingCalls.get());pending.disconnect();
        var blocking=new BlockingInput();var blocked=new SseCache<EventResponse>("Fault-SSE-Block",EventResponse.class,e->"x");
        blocked.start(CompletableFuture.completedFuture(new Response(blocking)));blocking.reading.await(2,TimeUnit.SECONDS);blocked.stop();Thread.sleep(50);
        result.addProperty("stop_explicitly_closed_InputStream",blocking.closed);blocking.close();
        var eofClient=new ApiClient(base+"/eof/v1","phase4-local-key","FaultEOF/"+label,false);
        var eof=new SseCache<EventResponse>("Fault-SSE-EOF",EventResponse.class,e->"x");
        eof.start(eofClient.streamEvents());Thread.sleep(6200);eof.stop();
        String records=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base+"/__records")).build(),HttpResponse.BodyHandlers.ofString()).body();
        long opens=JsonParser.parseString(records).getAsJsonObject().getAsJsonArray("records").asList().stream()
            .map(JsonElement::getAsJsonObject).filter(r->r.get("kind").getAsString().equals("sse_open")
                &&r.getAsJsonObject("headers").get("User-Agent").getAsString().equals("FaultEOF/"+label)).count();
        result.addProperty("SSE_requests_after_EOF",opens);
        var cache=new RelayCache();JsonObject snapshot=new JsonObject();snapshot.addProperty("type","snapshot");JsonArray events=new JsonArray();
        for(int i=0;i<256;i++){var e=new JsonObject();e.addProperty("server_id",i);e.addProperty("server_type","anarchy");events.add(e);}
        snapshot.add("events",events);String json=snapshot.toString();cache.updateFromSnapshot(json);
        var stop=new AtomicBoolean();var partial=new AtomicBoolean();Thread reader=new Thread(()->{
            while(!stop.get()){int size=cache.getEvents().size();if(size!=256)partial.set(true);}
        },"Fault-Cache-Reader");reader.start();for(int i=0;i<120;i++)cache.updateFromSnapshot(json);stop.set(true);reader.join();
        result.addProperty("partial_cache_publication_observed",partial.get());
        var timeout=new RelayClient(base.replace("https://","wss://")+"/noauth/v1/relay","phase4-local-key","FaultAuth/"+label,false);
        timeout.connect();Thread.sleep(31500);timeout.disconnect();Thread.sleep(200);
        records=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base+"/__records")).build(),HttpResponse.BodyHandlers.ofString()).body();
        var rows=JsonParser.parseString(records).getAsJsonObject().getAsJsonArray("records");long authOpens=0,authCloses=0;
        for(var v:rows){var r=v.getAsJsonObject();if(r.get("kind").getAsString().equals("ws_open")&&r.getAsJsonObject("headers").get("User-Agent").getAsString().equals("FaultAuth/"+label))authOpens++;
            if(r.get("kind").getAsString().equals("ws_closed")&&r.get("path").getAsString().contains("noauth")&&r.get("user_agent").getAsString().equals("FaultAuth/"+label))authCloses++;}
        result.addProperty("noauth_opens",authOpens);result.addProperty("noauth_closes",authCloses);
        result.addProperty("noauth_unclosed_after_disconnect",authOpens-authCloses);
        Files.writeString(Path.of(args[2]),GsonHolder.INSTANCE.toJson(result));System.out.println("[FAULT-PROBE] "+result);
    }
}
