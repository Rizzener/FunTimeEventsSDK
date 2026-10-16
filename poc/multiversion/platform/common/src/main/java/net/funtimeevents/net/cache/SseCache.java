package net.funtimeevents.net.cache;
import net.funtimeevents.util.*;
import com.google.gson.*;
import java.io.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class SseCache<T> {
    private record State<T>(List<T> data,long updated) {}
    private final String threadName;
    private final Class<T> type;
    private final Function<T,String> keyExtractor;
    private volatile State<T> state=new State<>(List.of(),0);
    private volatile Run current;
    private final class Run {
        final Supplier<CompletableFuture<HttpResponse<InputStream>>> source;
        final boolean reconnect;
        volatile boolean stopping;
        volatile CompletableFuture<HttpResponse<InputStream>> future;
        volatile InputStream stream;
        volatile Thread thread;
        Run(Supplier<CompletableFuture<HttpResponse<InputStream>>> source,boolean reconnect){this.source=source;this.reconnect=reconnect;}
    }
    public SseCache(String threadName,Class<T> type,Function<T,String> keyExtractor){this.threadName=threadName;this.type=type;this.keyExtractor=keyExtractor;}
    /** Binary-compatible one-shot entrypoint. A completed future cannot create a new request. */
    public void start(CompletableFuture<HttpResponse<InputStream>> future){startInternal(()->future,false);}
    /** Actual SDK uses a fresh request factory for every reconnect attempt. */
    public void startWith(Supplier<CompletableFuture<HttpResponse<InputStream>>> factory){startInternal(factory,true);}
    private synchronized void startInternal(Supplier<CompletableFuture<HttpResponse<InputStream>>> factory,boolean reconnect) {
        if(current!=null)return;
        var run=new Run(Objects.requireNonNull(factory),reconnect);current=run;state=new State<>(List.of(),0);
        run.thread=new Thread(()->read(run),threadName);run.thread.setDaemon(true);run.thread.start();
    }
    private boolean active(Run run){return current==run&&!run.stopping;}
    public void stop() {
        Run run;
        synchronized(this){run=current;current=null;state=new State<>(List.of(),0);if(run!=null)run.stopping=true;}
        if(run==null)return;
        var future=run.future;if(future!=null)future.cancel(true);
        close(run.stream);if(run.thread!=null)run.thread.interrupt();
        FteLogger.info(FteLogger.CACHE,threadName+" stopped");
    }
    public List<T> getData(){var s=state;return s.updated==0||System.nanoTime()-s.updated>5_000_000_000L?List.of():s.data;}
    private void close(InputStream stream) {
        if(stream!=null)try{stream.close();}catch(IOException e){FteLogger.warn(FteLogger.CACHE,threadName+" close failed: "+e.getClass().getSimpleName());}
    }
    private void read(Run run) {
        try {
            while(active(run)) {
                try {
                    var future=Objects.requireNonNull(run.source.get());run.future=future;
                    future.whenComplete((response,error)->{if(response!=null&&!active(run))close(response.body());});
                    if(!active(run)){future.cancel(true);break;}
                    var response=future.get(20,TimeUnit.SECONDS);run.stream=response.body();
                    if(!active(run)){close(run.stream);break;}
                    if(response.statusCode()!=200)FteLogger.warn(FteLogger.CACHE,threadName+" SSE HTTP "+response.statusCode());
                    else try(var reader=new BufferedReader(new InputStreamReader(run.stream,StandardCharsets.UTF_8))) {
                        String line;var data=new StringBuilder();
                        while(active(run)&&(line=reader.readLine())!=null) {
                            if(line.startsWith("data:")){String piece=line.substring(5);if(piece.startsWith(" "))piece=piece.substring(1);if(data.length()>0)data.append('\n');data.append(piece);}
                            else if(line.isEmpty()&&data.length()>0){process(run,data.toString());data.setLength(0);}
                        }
                    }
                }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                catch(Exception e){if(active(run))FteLogger.warn(FteLogger.CACHE,threadName+" SSE error: "+e.getClass().getSimpleName());else FteLogger.debug(FteLogger.CACHE,threadName+" reader stopped: "+e.getClass().getSimpleName());}
                finally {close(run.stream);run.stream=null;var f=run.future;if(f!=null&&!f.isDone())f.cancel(true);run.future=null;}
                if(!run.reconnect||!active(run))break;
                try{Thread.sleep(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            }
        }finally {synchronized(this){if(current==run)current=null;}}
    }
    private void process(Run run,String json) {
        try {
            var root=JsonParser.parseString(json);if(root==null||root.isJsonNull())return;
            JsonArray items;
            if(root.isJsonArray())items=root.getAsJsonArray();
            else {var obj=root.getAsJsonObject();if(!obj.has("data")||obj.get("data").isJsonNull())return;items=obj.getAsJsonArray("data");}
            Map<String,T> values=new LinkedHashMap<>();
            for(var item:items){T value=GsonHolder.INSTANCE.fromJson(item,type);values.put(keyExtractor.apply(Objects.requireNonNull(value)),value);}
            var list=List.copyOf(values.values());synchronized(this){if(active(run))state=new State<>(list,System.nanoTime());}
        }catch(RuntimeException e){FteLogger.warn(FteLogger.CACHE,threadName+" invalid SSE data: "+e.getClass().getSimpleName());}
    }
}
