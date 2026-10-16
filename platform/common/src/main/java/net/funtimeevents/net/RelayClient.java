package net.funtimeevents.net;

import net.funtimeevents.model.*;
import net.funtimeevents.net.cache.RelayCache;
import net.funtimeevents.spi.*;
import net.funtimeevents.util.*;
import com.google.gson.JsonParser;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Same text protocol; one writer and connection identity checks on every callback. */
public final class RelayClient implements PayloadSender {
    private final String wsUrl,apiKey,userAgent;
    private final HttpClient httpClient;
    private final SessionFence fence;
    private final long authTimeoutMillis,idleTimeoutMillis,reconnectMillis;
    private final PayloadQueue pending=new PayloadQueue();
    private final AtomicLong sendFailures=new AtomicLong(),lastQueueWarning=new AtomicLong();
    private volatile RelayCache cache;
    private volatile Connection current;
    private volatile boolean running=true,started,stopped;
    private volatile Thread reconnectThread,writerThread;
    private static final class Connection {
        final CountDownLatch auth=new CountDownLatch(1),closed=new CountDownLatch(1);
        volatile WebSocket socket;
        volatile CompletableFuture<WebSocket> handshake;
        volatile boolean authenticated,authSent;
        volatile long lastSnapshot=System.nanoTime();
    }
    public RelayClient(String wsUrl,String apiKey,String userAgent,boolean useCompression) {
        this(wsUrl,apiKey,userAgent,useCompression,null);
    }
    public RelayClient(String wsUrl,String apiKey,String userAgent,boolean useCompression,SessionFence fence) {
        this(wsUrl,apiKey,userAgent,useCompression,fence,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),30_000,15_000,1000);
    }
    RelayClient(String wsUrl,String apiKey,String userAgent,boolean useCompression,SessionFence fence,HttpClient client,
            long authTimeoutMillis,long idleTimeoutMillis,long reconnectMillis) {
        this.wsUrl=wsUrl;this.apiKey=apiKey;this.userAgent=userAgent;this.fence=fence;httpClient=client;
        this.authTimeoutMillis=authTimeoutMillis;this.idleTimeoutMillis=idleTimeoutMillis;this.reconnectMillis=reconnectMillis;
    }
    public void setCache(RelayCache cache){this.cache=cache;}
    public synchronized void connect() {
        if(stopped)throw new IllegalStateException("RelayClient closed");
        if(started)return;started=true;
        writerThread=daemon(this::writeLoop,"FTE-Relay-IO");reconnectThread=daemon(this::connectLoop,"FTE-Relay");
        writerThread.start();reconnectThread.start();
    }
    private static Thread daemon(Runnable action,String name){var t=new Thread(action,name);t.setDaemon(true);return t;}
    public void disconnect() {
        Connection c;
        synchronized(this){if(stopped)return;stopped=true;running=false;c=current;current=null;}
        pending.close();if(c!=null)close(c);
        if(reconnectThread!=null)reconnectThread.interrupt();if(writerThread!=null)writerThread.interrupt();httpClient.shutdownNow();
    }
    private void connectLoop() {
        long backoff=reconnectMillis;
        while(running&&!stopped) {
            var c=new Connection();
            synchronized(this){if(!running||stopped)break;current=c;}
            try {
                FteLogger.info(FteLogger.RELAY,"connecting to "+wsUrl);
                c.handshake=httpClient.newWebSocketBuilder().header("User-Agent",userAgent).connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(URI.create(wsUrl),new Listener(c));
                c.handshake.get(15,TimeUnit.SECONDS);
                if(!c.auth.await(authTimeoutMillis,TimeUnit.MILLISECONDS))FteLogger.warn(FteLogger.RELAY,"auth timeout; closing connection");
                else if(c.authenticated) {
                    backoff=reconnectMillis;
                    while(running&&current==c&&!c.closed.await(Math.min(1000,idleTimeoutMillis),TimeUnit.MILLISECONDS)) {
                        if(System.nanoTime()-c.lastSnapshot>TimeUnit.MILLISECONDS.toNanos(idleTimeoutMillis)) {
                            FteLogger.warn(FteLogger.RELAY,"snapshot watchdog timeout");break;
                        }
                    }
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            catch(Exception e){if(running)FteLogger.warn(FteLogger.RELAY,"Connect failed: "+e.getClass().getSimpleName());}
            finally {finish(c,false);}
            if(!running||stopped)break;
            try{Thread.sleep(backoff);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            backoff=Math.min(backoff*2,60_000);
        }
    }
    private void finish(Connection c,boolean authRejected) {
        boolean active;
        synchronized(this) {
            active=current==c;
            if(active){current=null;c.authenticated=false;if(authRejected)running=false;}
        }
        close(c);pending.wake();
        if(active&&authRejected){pending.close();if(writerThread!=null)writerThread.interrupt();httpClient.shutdownNow();}
    }
    private static void close(Connection c) {
        c.auth.countDown();c.closed.countDown();
        var handshake=c.handshake;if(handshake!=null&&!handshake.isDone())handshake.cancel(true);
        var socket=c.socket;if(socket!=null)socket.abort();
    }
    private final class Listener implements WebSocket.Listener {
        private final Connection c;
        private final StringBuilder buffer=new StringBuilder();
        Listener(Connection c){this.c=c;}
        public void onOpen(WebSocket socket) {
            synchronized(RelayClient.this) {
                if(current!=c||!running||stopped){socket.abort();return;}
                c.socket=socket;
            }
            socket.request(1);pending.wake();
        }
        public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last) {
            if(current==c&&running&&!stopped) {
                buffer.append(data);
                if(last){String message=buffer.toString();buffer.setLength(0);process(c,message);}
            }
            socket.request(1);return CompletableFuture.completedFuture(null);
        }
        public CompletionStage<?> onClose(WebSocket socket,int statusCode,String reason){finish(c,statusCode==4001);return CompletableFuture.completedFuture(null);}
        public void onError(WebSocket socket,Throwable error){if(current==c)FteLogger.warn(FteLogger.RELAY,"WebSocket error: "+error.getClass().getSimpleName());finish(c,false);}
    }
    private void process(Connection c,String text) {
        try {
            var json=JsonParser.parseString(text).getAsJsonObject();String type=json.get("type").getAsString();
            if(type.equals("auth_ok")) {
                synchronized(this){if(current!=c||!running||stopped||!c.authSent)return;c.authenticated=true;c.lastSnapshot=System.nanoTime();}
                c.auth.countDown();pending.wake();
            } else if(type.equals("snapshot")) {
                var target=cache;
                if(target!=null)target.updateFromSnapshot(text,publication->{
                    synchronized(this){if(current==c&&running&&!stopped&&c.authenticated){publication.run();c.lastSnapshot=System.nanoTime();}}
                });
                else synchronized(this){if(current==c&&c.authenticated)c.lastSnapshot=System.nanoTime();}
            }
        }catch(RuntimeException e){FteLogger.warn(FteLogger.RELAY,"Invalid relay message: "+e.getClass().getSimpleName());}
    }
    private void writeLoop() {
        try {
            while(running&&!stopped) {
                var c=current;
                if(c!=null&&c.socket!=null&&!c.authSent) {
                    synchronized(this){if(current!=c||c.authSent)continue;c.authSent=true;}
                    var auth=new HashMap<String,Object>();auth.put("type","auth");if(apiKey!=null&&!apiKey.isBlank())auth.put("api_key",apiKey);
                    send(c,GsonHolder.INSTANCE.toJson(auth),SessionFence.unscoped());continue;
                }
                if(c!=null&&c.authenticated) {
                    var item=pending.pollNow();
                    if(item!=null) {
                        if(!item.ticket().valid())continue;
                        try{send(c,GsonHolder.INSTANCE.toJson(Map.of("type",item.type(),"body",item.body())),item.ticket());}
                        catch(RuntimeException e){sendFailures.incrementAndGet();FteLogger.warn(FteLogger.RELAY,"Payload serialization failed: "+e.getClass().getSimpleName());}
                        continue;
                    }
                }
                pending.idleWait();
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(RuntimeException e){FteLogger.warn(FteLogger.RELAY,"Relay writer failed: "+e.getClass().getSimpleName());disconnect();}
    }
    private void send(Connection c,String text,SessionFence.Ticket ticket) {
        CompletableFuture<WebSocket> future=null;
        try {
            if(current!=c||!running||stopped||!ticket.valid())return;
            future=ticket.dispatch(()->{
                synchronized(this) {
                    if(current!=c||!running||stopped)return CompletableFuture.failedFuture(new CancellationException("Obsolete WebSocket"));
                    return c.socket.sendText(text,true);
                }
            });
            // One writer, including auth. No second text send before this result.
            future.get(15,TimeUnit.SECONDS);
        }catch(InterruptedException e){if(future!=null)future.cancel(true);Thread.currentThread().interrupt();}
        catch(Exception e){sendFailures.incrementAndGet();FteLogger.warn(FteLogger.RELAY,"Send failed; delivery may be unknown, no replay: "+e.getClass().getSimpleName());finish(c,false);}
    }
    private void offer(String type,Object body) {
        if(body==null){FteLogger.warn(FteLogger.RELAY,"Ignored null payload");return;}
        var ticket=fence==null?SessionFence.unscoped():fence.capture();
        if(!pending.offer(type,body,ticket)) {
            long now=System.nanoTime(),old=lastQueueWarning.get();
            if((old==0||now-old>TimeUnit.SECONDS.toNanos(5))&&lastQueueWarning.compareAndSet(old,now))FteLogger.warn(FteLogger.RELAY,"Relay queue rejected payload: "+pending.stats());
        }
    }
    PayloadQueue.Stats queueStats(){return pending.stats();}
    long sendFailures(){return sendFailures.get();}
    public void sendTabPlayers(TabPlayersPayload p){offer("players",p);}
    public void sendBan(BanPayload p){offer("bans",p);}
    public void sendCopperDungeon(DungeonPayload p){offer("copper_dungeon",p);}
    public void sendWardenCity(DungeonPayload p){offer("warden_city",p);}
    public void sendHellMap(HellMapPayload p){offer("hell_map",p);}
    public void sendEventCoordinates(EventCoordinatesPayload p){offer("coordinates",p);}
    public void sendMinePlayers(MinePlayersAroundPayload p){offer("players_around",p);}
}
