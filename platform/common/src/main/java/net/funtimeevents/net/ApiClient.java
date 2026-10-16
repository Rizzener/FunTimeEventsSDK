package net.funtimeevents.net;

import net.funtimeevents.model.*;
import net.funtimeevents.spi.*;
import net.funtimeevents.util.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/** Original REST protocol with bounded preparation and no JSON/gzip on the caller. */
public final class ApiClient implements PayloadSender,AutoCloseable {
    private static final Duration TIMEOUT=Duration.ofSeconds(15);
    private final String baseUrl,apiKey,userAgent;
    private final boolean useCompression;
    private final HttpClient httpClient;
    private final SessionFence fence;
    private final UnaryOperator<byte[]> compressor;
    private final PayloadQueue outbound=new PayloadQueue();
    private final ThreadPoolExecutor preparation=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"FTE-HTTP-Request");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Set<CompletableFuture<?>> requests=ConcurrentHashMap.newKeySet();
    private final AtomicLong failures=new AtomicLong(),lastQueueWarning=new AtomicLong();
    private final AtomicLong rejectedRequests=new AtomicLong(),lastRequestWarning=new AtomicLong();
    private volatile boolean closed,writerStarted;
    private volatile Thread writer;
    public ApiClient(String baseUrl,String apiKey,String userAgent,boolean useCompression) {
        this(baseUrl,apiKey,userAgent,useCompression,null,ApiClient::gzip);
    }
    public ApiClient(String baseUrl,String apiKey,String userAgent,boolean useCompression,SessionFence fence) {
        this(baseUrl,apiKey,userAgent,useCompression,fence,ApiClient::gzip);
    }
    ApiClient(String baseUrl,String apiKey,String userAgent,boolean useCompression,SessionFence fence,UnaryOperator<byte[]> compressor) {
        this.baseUrl=baseUrl.endsWith("/")?baseUrl.substring(0,baseUrl.length()-1):baseUrl;
        this.apiKey=apiKey;this.userAgent=userAgent;this.useCompression=useCompression;this.fence=fence;this.compressor=compressor;
        httpClient=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT).build();
    }
    public void sendTabPlayers(TabPlayersPayload p){postJson("/players",p);}
    public void sendBan(BanPayload p){postJson("/bans",p);}
    public void sendCopperDungeon(DungeonPayload p){postJson("/copper-dungeon",p);}
    public void sendWardenCity(DungeonPayload p){postJson("/warden-city",p);}
    public void sendHellMap(HellMapPayload p){postJson("/events/hell-map",p);}
    public void sendMinePlayers(MinePlayersAroundPayload p){postJson("/mines/players-around",p);}
    public void sendEventCoordinates(EventCoordinatesPayload p){postJson("/events/coordinates",p);}
    private void postJson(String path,Object body) {
        if(body==null){FteLogger.warn(FteLogger.API,"Ignored null payload");return;}
        var ticket=fence==null?SessionFence.unscoped():fence.capture();
        if(outbound.offer(path,body,ticket))startWriter();
        else warnQueue();
    }
    private void warnQueue() {
        long now=System.nanoTime(),previous=lastQueueWarning.get();
        if((previous==0||now-previous>TimeUnit.SECONDS.toNanos(5))&&lastQueueWarning.compareAndSet(previous,now))
            FteLogger.warn(FteLogger.API,"POST queue rejected a payload: "+outbound.stats());
    }
    private synchronized void startWriter() {
        if(writerStarted||closed)return;writerStarted=true;
        writer=new Thread(this::writeLoop,"FTE-HTTP-POST");writer.setDaemon(true);writer.start();
    }
    private void writeLoop() {
        try {
            while(!closed) {
                var item=outbound.take(()->!closed);if(item==null)break;
                try {
                    var request=buildPostRequest(item.type(),GsonHolder.INSTANCE.toJson(item.body()));
                    if(!item.ticket().valid())continue;
                    exchange(request,item.ticket());
                }catch(RuntimeException e){failures.incrementAndGet();FteLogger.warn(FteLogger.API,"POST preparation failed: "+e.getClass().getSimpleName());}
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private HttpRequest.Builder headers(String path) {
        var builder=HttpRequest.newBuilder(URI.create(baseUrl+path)).header("User-Agent",userAgent);
        if(apiKey!=null&&!apiKey.isBlank())builder.header("X-API-Key",apiKey);
        return builder;
    }
    private HttpRequest buildPostRequest(String path,String json) {
        byte[] body=json.getBytes(StandardCharsets.UTF_8);
        // Failure propagates before any request is submitted; never a gzip header on plain bytes.
        if(useCompression)body=compressor.apply(body);
        var builder=headers(path).header("Content-Type","application/json").timeout(TIMEOUT);
        if(useCompression)builder.header("Content-Encoding","gzip");
        return builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }
    private static byte[] gzip(byte[] data) {
        try(var bytes=new ByteArrayOutputStream()) {
            try(var zip=new GZIPOutputStream(bytes)){zip.write(data);}
            return bytes.toByteArray();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private String exchange(HttpRequest request,SessionFence.Ticket ticket) {
        CompletableFuture<HttpResponse<String>> future=null;
        try {
            if(closed||!ticket.valid())return null;
            future=ticket.dispatch(()->{synchronized(this){
                if(closed)return CompletableFuture.failedFuture(new CancellationException("ApiClient closed"));
                return httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString());
            }});
            var response=future.get(16,TimeUnit.SECONDS);
            if(response.statusCode()>=400)FteLogger.warn(FteLogger.API,"HTTP "+response.statusCode()+" "+request.uri().getPath());
            return response.body();
        }catch(InterruptedException e){if(future!=null)future.cancel(true);Thread.currentThread().interrupt();return null;}
        catch(Exception e){if(future!=null)future.cancel(true);if(!closed&&ticket.valid()){failures.incrementAndGet();FteLogger.warn(FteLogger.API,"HTTP request failed: "+e.getClass().getSimpleName());}return null;}
    }
    private String get(String path,Map<String,String> params) {
        try {
            String query=(params==null?Map.<String,String>of():params).entrySet().stream()
                .filter(e->e.getValue()!=null&&!e.getValue().isEmpty())
                .map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            return exchange(headers(path+(query.isEmpty()?"":"?"+query)).timeout(TIMEOUT).GET().build(),SessionFence.unscoped());
        }catch(RuntimeException e){FteLogger.warn(FteLogger.API,"GET preparation failed: "+e.getClass().getSimpleName());return null;}
    }
    private <T> CompletableFuture<T> prepare(Callable<T> action) {
        if(closed)return CompletableFuture.completedFuture(null);
        var result=new CompletableFuture<T>();requests.add(result);result.whenComplete((value,error)->requests.remove(result));
        try {
            preparation.execute(()->{
                if(closed||result.isCancelled()){result.complete(null);return;}
                try{result.complete(action.call());}catch(Exception e){result.completeExceptionally(e);}
            });
        }catch(RejectedExecutionException e){
            long count=rejectedRequests.incrementAndGet(),now=System.nanoTime(),previous=lastRequestWarning.get();
            if((previous==0||now-previous>TimeUnit.SECONDS.toNanos(5))&&lastRequestWarning.compareAndSet(previous,now))
                FteLogger.warn(FteLogger.API,"Request preparation queue full or closed; rejected="+count);
            result.complete(null);
        }
        return result;
    }
    public CompletableFuture<String> getEvents(Map<String,String> params){return prepare(()->get("/events",params));}
    public CompletableFuture<String> getMines(Map<String,String> params){return prepare(()->get("/mines",params));}
    public CompletableFuture<PlayersListResponse> getPlayers(Map<String,String> params){return prepare(()->parse(get("/players",params),PlayersListResponse.class));}
    public CompletableFuture<BansListResponse> getBans(Map<String,String> params){return prepare(()->parse(get("/bans",params),BansListResponse.class));}
    public CompletableFuture<String> getCopperDungeon(){return prepare(()->get("/copper-dungeon",Map.of()));}
    public CompletableFuture<String> getWardenCity(){return prepare(()->get("/warden-city",Map.of()));}
    public CompletableFuture<CaptchaResponse> solveCaptcha(String base64){return prepare(()->{
        String json;
        try{json=exchange(buildPostRequest("/captcha",GsonHolder.INSTANCE.toJson(new CaptchaPayload(base64))),SessionFence.unscoped());}
        catch(RuntimeException e){failures.incrementAndGet();FteLogger.warn(FteLogger.API,"Captcha preparation failed: "+e.getClass().getSimpleName());return null;}
        return parse(json,CaptchaResponse.class);
    });}
    private static <T>T parse(String json,Class<T> type){return json==null?null:GsonHolder.INSTANCE.fromJson(json,type);}
    public CompletableFuture<HttpResponse<InputStream>> streamEvents(){return stream("/events/stream");}
    public CompletableFuture<HttpResponse<InputStream>> streamUserEvents(){return stream("/events/stream?event_type=user");}
    public CompletableFuture<HttpResponse<InputStream>> streamMines(){return stream("/mines/stream");}
    public CompletableFuture<HttpResponse<InputStream>> streamCopperDungeons(){return stream("/copper-dungeon/stream");}
    public CompletableFuture<HttpResponse<InputStream>> streamWardenCities(){return stream("/warden-city/stream");}
    private CompletableFuture<HttpResponse<InputStream>> stream(String path) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("ApiClient closed"));
        try{synchronized(this){if(closed)return CompletableFuture.failedFuture(new IllegalStateException("ApiClient closed"));
            return httpClient.sendAsync(headers(path).header("Accept","text/event-stream").timeout(TIMEOUT).GET().build(),HttpResponse.BodyHandlers.ofInputStream());}}
        catch(RuntimeException e){return CompletableFuture.failedFuture(e);}
    }
    PayloadQueue.Stats queueStats(){return outbound.stats();}
    long failures(){return failures.get();}
    int queuedRequests(){return preparation.getQueue().size();}
    int outstandingRequests(){return requests.size();}
    boolean resourcesTerminated(){return httpClient.isTerminated()&&preparation.isTerminated()&&(writer==null||!writer.isAlive());}
    public synchronized void close() {
        if(closed)return;closed=true;outbound.close();
        for(var future:requests)future.cancel(true);requests.clear();
        preparation.shutdownNow();if(writer!=null)writer.interrupt();httpClient.shutdownNow();
    }
}
