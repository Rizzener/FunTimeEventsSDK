package net.funtimeevents.poc.consumer;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.funtimeevents.model.*;
import net.funtimeevents.poc.api.TrackerSliceAPI;
import net.funtimeevents.poc.platform.*;
import net.funtimeevents.poc.trackers.*;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Display;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Observes actual game state; never calls a tracker scan or a message event invoker. */
public final class Phase3Consumer implements PayloadSender {
    private final JsonArray sessions = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(
        getClass().getResourceAsStream(System.getProperty("fte.phase4.stage")!=null?"/phase4/tracker-sessions.json":"/phase3/tracker-sessions.json")), StandardCharsets.UTF_8)).getAsJsonArray();
    private final JsonArray payloads = new JsonArray(), results = new JsonArray(), callbacks = new JsonArray();
    private final JsonArray chunkEvents = new JsonArray();
    private final ConcurrentLinkedQueue<Disconnect> disconnects = new ConcurrentLinkedQueue<>();
    private int disconnectWaitTicks, disconnectDeferredTicks;
    private record Disconnect(ClientPacketListener handler, String thread) {}
    private TrackerRuntime runtime;
    private final boolean phase4a = "a".equals(System.getProperty("fte.phase4.stage"));
    private final boolean phase4online = Set.of("b","c").contains(System.getProperty("fte.phase4.stage",""));
    private final Phase4Network network;
    private final boolean phase4flags=Boolean.getBoolean("fte.phase4.flags");
    private int observedJoins,observedDisconnects,totalPeriodic;
    private final com.sun.management.ThreadMXBean allocationBean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    private final long renderThreadId = Thread.currentThread().threadId();
    private final Map<String,long[]> callbackPerformance = new LinkedHashMap<>();
    private long tickStarted, tickAllocated, messageStarted, messageAllocated;
    private final boolean measureThreadCpu=Boolean.getBoolean("fte.phase5.threadCpu");
    private long tickCpuStarted;
    private final JsonArray cpuAndWallSpans=new JsonArray();
    private boolean tickWasActive;
    private ClientPacketListener current;
    private int session, ticks, delay = -1, payloadsAfterLeave;
    private boolean ready, ended, acked, bossChanged, bossRemoved, finished;
    private long entitiesBefore, displaysBefore, blocksBefore;
    private Instant joinedAt;

    private Phase3Consumer() {
        require(allocationBean.isThreadAllocatedMemorySupported(),"Thread allocation counter unavailable");
        if(!allocationBean.isThreadAllocatedMemoryEnabled())allocationBean.setThreadAllocatedMemoryEnabled(true);
        if(measureThreadCpu) {
            require(allocationBean.isThreadCpuTimeSupported(),"Thread CPU time unavailable");
            if(!allocationBean.isThreadCpuTimeEnabled())allocationBean.setThreadCpuTimeEnabled(true);
        }
        // Start observers precede the SDK listener; end observers in install()
        // precede fixture bookkeeping. No SDK source/profiling hooks are added.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickWasActive=current!=null;
            if(measureThreadCpu)tickCpuStarted=allocationBean.getThreadCpuTime(renderThreadId);
            tickAllocated=allocationBean.getThreadAllocatedBytes(renderThreadId);tickStarted=System.nanoTime();
        });
        ClientReceiveMessageEvents.GAME.register((message,overlay)->beforeMessage());
        ClientReceiveMessageEvents.CHAT.register((message,signed,sender,type,stamp)->beforeMessage());
        if(phase4a) {
            Phase4ApiCheck.run();
            runtime=net.funtimeevents.bootstrap.Bootstrap.getInstance().runtime();
            network=null;
        } else if(phase4online) {
            Phase4ApiCheck.run();net.funtimeevents.api.FunTimeEventsAPI.shutdown();
            network=new Phase4Network();runtime=net.funtimeevents.bootstrap.Bootstrap.getInstance().runtime();
        } else {runtime=TrackerSliceAPI.start(this);network=null;}
    }
    private void beforeMessage() {
        messageAllocated=allocationBean.getThreadAllocatedBytes(renderThreadId);messageStarted=System.nanoTime();
    }
    private void performance(String category,long nanos,long bytes) {
        long[] s=callbackPerformance.computeIfAbsent(category,key->new long[5]);
        s[0]++;s[1]+=nanos;s[2]=Math.max(s[2],nanos);s[3]+=bytes;s[4]=Math.max(s[4],bytes);
    }

    public static void start() { new Phase3Consumer().install(); }
    private void install() {
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->chunkEvent("load",chunk.getPos().toString()));
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->chunkEvent("unload",chunk.getPos().toString()));
        var loader=FabricLoader.getInstance();
        require(!loader.isDevelopmentEnvironment(),"Development mode");
        require(loader.getModContainer("fte-multiversion-poc").orElseThrow().getOrigin().getKind().name().equals("NESTED"),"SDK not nested");
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client) -> {
            current=handler;joinedAt=Instant.now();
            observedJoins++;
            entitiesBefore=TrackerMetrics.entities();displaysBefore=TrackerMetrics.displays();blocksBefore=TrackerMetrics.blocks();
            require(runtime.bans().isJoined() && runtime.bans().joins()==(phase4flags?1:session),"JOIN duplicated/missing");
            if(phase4a||phase4online) { if(phase4a)Phase4ApiCheck.offline().build();else network.sameBuilder();
                require(net.funtimeevents.bootstrap.Bootstrap.getInstance().runtime()==runtime,"Duplicate public runtime"); }
            else require(TrackerSliceAPI.start(this)==runtime,"Duplicate runtime");
            System.out.println("[PHASE3] JOIN session="+session+" fixture="+fixture().get("name").getAsString());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> disconnects.add(new Disconnect(handler,Thread.currentThread().getName())));
        ClientReceiveMessageEvents.GAME.register((message,overlay) -> observe("GAME",message));
        ClientReceiveMessageEvents.CHAT.register((message,signed,sender,type,stamp) -> observe("CHAT",message));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(finished)return;
            long elapsed=System.nanoTime()-tickStarted;
            long observedCpu=measureThreadCpu?allocationBean.getThreadCpuTime(renderThreadId)-tickCpuStarted:0;
            long allocated=allocationBean.getThreadAllocatedBytes(renderThreadId)-tickAllocated;
            performance(tickWasActive?"sdk_tick_active":"sdk_tick_idle",elapsed,allocated);
            if(measureThreadCpu && elapsed>=1_000_000) {
                var span=new JsonObject();span.addProperty("session",session);span.addProperty("tick",ticks);
                span.addProperty("active",tickWasActive);span.addProperty("wall_ns",elapsed);span.addProperty("thread_cpu_ns",observedCpu);
                cpuAndWallSpans.add(span);
            }
            try {
                if(++ticks>20000)throw new IllegalStateException("Client tick timeout");
                Disconnect leave;
                while((leave=disconnects.peek())!=null) {
                    if(leave.handler()!=current){disconnects.poll();continue;}
                    // Netty may notify between the SDK END_TICK listener and this listener.
                    // The contract drains on the next tick; do not require same-tick delivery.
                    if(runtime.bans().isJoined()) {
                        disconnectDeferredTicks++;
                        require(++disconnectWaitTicks<=2,"DISCONNECT handoff not processed within two ticks");
                        break;
                    }
                    disconnects.poll();disconnectWaitTicks=0;
                    require(ended,"Disconnected before END");
                    require(!runtime.bans().isJoined() && runtime.bans().state().serverId()==-1
                        && runtime.bans().state().serverIp()==null && !runtime.bans().state().onFuntime(),"Stale server state");
                    observedDisconnects++;
                    require(runtime.bans().disconnects()==(phase4flags?1:session),"Duplicate/missing disconnect");
                    if(network!=null) {
                        String thread=leave.thread();int count=0;
                        for(var v:expectedPayloads().entrySet())count+=v.getValue().getAsJsonArray().size();
                        network.capture(session,count).whenComplete((rows,error)->client.execute(()->{
                            try {if(error!=null)throw new IllegalStateException(error);for(var row:rows)payloads.add(row);completeLeave(thread);}
                            catch(Throwable failure){failure.printStackTrace();finish("FAIL",failure.toString());}
                        }));
                    } else completeLeave(leave.thread());
                }
                if(session==0 && ticks>=100 && (network==null||network.ready()))connect();
                else if(delay>=0 && --delay==0) {
                    require(payloads.size()==payloadsAfterLeave,"Payload emitted after disconnect");
                    if(session==sessions.size())finish("PASS",null);else connect();
                }
                if(current!=null && ready && !acked) {
                    int at=runtime.sessionTicks();
                    if(fixture().has("boss") && at>=120 && !bossChanged) { current.sendCommand("phase3 boss9");bossChanged=true; }
                    if(fixture().has("boss") && at>=220 && !bossRemoved) { current.sendCommand("phase3 remove");bossRemoved=true; }
                    if(at>=fixture().get("wait_ticks").getAsInt()) {
                        snapshot();acked=true;current.sendCommand("phase3 next");
                    }
                }
            } catch(Throwable error) { error.printStackTrace();finish("FAIL",error.toString()); }
        });
    }
    private JsonObject fixture() { return sessions.get(session-1).getAsJsonObject(); }
    private void chunkEvent(String action,String position) {
        if(current==null || chunkEvents.size()>=256)return;
        var row=new JsonObject();row.addProperty("action",action);row.addProperty("position",position);row.addProperty("tick",runtime.sessionTicks());chunkEvents.add(row);
    }
    private void completeLeave(String thread) {
        verifySession(thread);current=null;payloadsAfterLeave=payloads.size();delay=1;
        if(phase4flags)totalPeriodic+=runtime.periodic();
        if(network!=null&&(!phase4flags||session<=9))network.menuCheck();
    }
    private void connect() {
        session++;ready=false;ended=false;acked=false;bossChanged=false;bossRemoved=false;delay=-1;chunkEvents.asList().clear();
        if(phase4flags) {network.reconfigure(session);runtime=net.funtimeevents.bootstrap.Bootstrap.getInstance().runtime();}
        if(network!=null)network.begin(session).whenComplete((v,error)->Minecraft.getInstance().execute(()->{
            if(error!=null)finish("FAIL",error.toString());else startConnection();
        }));else startConnection();
    }
    private void startConnection() {
        var data=new ServerData("Phase3 "+session,fixture().get("host").getAsString(),ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(),Minecraft.getInstance(),
            ServerAddress.parseString("127.0.0.1:"+Integer.getInteger("fte.phase3.port")),data,false,null);
    }
    private void observe(String channel,Component component) {
        if(session==0||finished)return;
        long elapsed=System.nanoTime()-messageStarted;
        long allocated=allocationBean.getThreadAllocatedBytes(renderThreadId)-messageAllocated;
        String text=component.getString();
        if(text.equals("PHASE3_READY "+session)){ready=true;return;}
        if(text.equals("PHASE3_END "+session)){ended=true;current.sendCommand("phase3 endseen");return;}
        for(var value:fixture().getAsJsonArray("messages")) {
            var m=value.getAsJsonObject();if(!text.contains(m.get("text").getAsString()))continue;
            String id=m.get("id").getAsString();
            performance(id.startsWith("ban-")?"ban_message":id.startsWith("event-")?"coordinates_message":"ordinary_message",elapsed,allocated);
            var row=new JsonObject();row.addProperty("session",session);row.addProperty("id",m.get("id").getAsString());
            row.addProperty("channel",channel);row.addProperty("plain",text);row.addProperty("hover",MinecraftText.hover(component));
            callbacks.add(row);System.out.println("[PHASE3] CALLBACK "+row);
        }
    }
    private void record(String type,Object payload) {
        var row=new JsonObject();row.addProperty("session",session);row.addProperty("tick",runtime.sessionTicks());
        row.addProperty("type",type);row.add("payload",GsonHolder.INSTANCE.toJsonTree(payload));payloads.add(row);
        System.out.println("[PHASE3] PAYLOAD "+row);
    }
    public void sendTabPlayers(TabPlayersPayload p){record("tab",p);}
    public void sendBan(BanPayload p){record("ban",p);}
    public void sendCopperDungeon(DungeonPayload p){record("copper",p);}
    public void sendWardenCity(DungeonPayload p){record("warden",p);}
    public void sendHellMap(HellMapPayload p){record("hell",p);}
    public void sendMinePlayers(MinePlayersAroundPayload p){record("mine",p);}
    public void sendEventCoordinates(EventCoordinatesPayload p){record("coordinates",p);}

    private void verifySession(String sourceThread) {
        var expected=expectedPayloads();var actual=new JsonObject();
        for(String type:expected.keySet())actual.add(type,new JsonArray());
        for(var value:payloads) {
            var row=value.getAsJsonObject();if(row.get("session").getAsInt()!=session)continue;
            String type=row.get("type").getAsString();int at=row.get("tick").getAsInt();
            if(network==null&&Set.of("copper","warden","mine","tab").contains(type))require(at%200==0,"Wrong periodic interval "+type+" "+at);
            if(network==null&&type.equals("hell"))require(at%100==0,"Wrong Hell interval "+at);
            actual.getAsJsonArray(type).add(normalize(row.get("payload").deepCopy()));
        }
        require(normalize(actual).equals(normalize(expected.deepCopy())),"Payload mismatch "+fixture().get("name")+": actual="+actual+" expected="+expected);
        for(var value:fixture().getAsJsonArray("messages")) {
            var input=value.getAsJsonObject();int count=0;
            for(var event:callbacks) {
                var row=event.getAsJsonObject();if(row.get("session").getAsInt()!=session||!row.get("id").equals(input.get("id")))continue;
                count++;require(row.get("channel").equals(input.get("channel")),"Wrong callback channel");
                require(row.get("hover").getAsString().equals(input.has("hover")?input.get("hover").getAsString():""),"Hover lost");
            }
            require(count==1,"Missing/duplicate real message callback "+input.get("id"));
        }
        long visits=TrackerMetrics.entities()-entitiesBefore;
        if(!Set.of("copper","warden").contains(fixture().get("name").getAsString()))require(visits==0,"Dungeon scanned outside active zone/host");
        var result=new JsonObject();result.addProperty("session",session);result.addProperty("name",fixture().get("name").getAsString());
        result.addProperty("status","PASS");result.addProperty("ticks",runtime.sessionTicks());
        result.addProperty("disconnect_source",sourceThread);result.addProperty("disconnect_handled",Thread.currentThread().getName());
        result.addProperty("state_cleared",true);result.addProperty("entity_visits",visits);
        result.addProperty("text_display_reads",TrackerMetrics.displays()-displaysBefore);result.addProperty("block_reads",TrackerMetrics.blocks()-blocksBefore);
        result.add("payloads",actual);results.add(result);System.out.println("[PHASE3] SESSION_PASS "+result);
    }
    private JsonElement normalize(JsonElement value) {
        if(value.isJsonObject()) {
            var obj=value.getAsJsonObject();
            for(String key:List.copyOf(obj.keySet())) {
                if(key.equals("active") && !obj.get(key).getAsString().equals("<ISO8601>")) {
                    var time=Instant.parse(obj.get(key).getAsString());
                    require(!time.isBefore(joinedAt) && !time.isAfter(Instant.now()),"Timestamp out of session");
                    obj.addProperty(key,"<ISO8601>");
                } else obj.add(key,normalize(obj.get(key)));
            }
        } else if(value.isJsonArray()) {
            List<JsonElement> list=new ArrayList<>();for(var item:value.getAsJsonArray())list.add(normalize(item));
            list.sort(Comparator.comparing(JsonElement::toString));var array=new JsonArray();list.forEach(array::add);return array;
        }
        return value;
    }
    private JsonObject expectedPayloads() {
        var expected=fixture().getAsJsonObject("expected").deepCopy();
        if(phase4a)for(String type:List.copyOf(expected.keySet()))expected.add(type,new JsonArray());
        if(phase4flags) {
            String disabled=switch(session){case 1->"ban";case 2->"coordinates";case 3->"warden";case 4->"mine";case 5->"tab";case 7->"hell";default->null;};
            if(disabled!=null)expected.add(disabled,new JsonArray());
        }
        return expected;
    }
    private void snapshot() {
        var world=Minecraft.getInstance().level;
        require(world!=null && VersionAccess.dimension(world).equals(fixture().get("dimension").getAsString()),"Wrong real client dimension");
        var row=new JsonObject();row.addProperty("session",session);row.addProperty("dimension",VersionAccess.dimension(world));
        row.addProperty("dimension_min_y",world.dimensionType().minY());row.addProperty("dimension_height",world.dimensionType().height());
        var displays=new JsonArray();int entityCount=0;
        for(var entity:world.entitiesForRendering()) {
            entityCount++;
            if(entity instanceof net.minecraft.world.entity.Display.TextDisplay) {
                var d=new JsonObject();d.addProperty("id",entity.getId());d.addProperty("x",entity.getX());d.addProperty("y",entity.getY());d.addProperty("z",entity.getZ());
                d.addProperty("text",((net.funtimeevents.poc.platform.mixin.TextDisplayAccessor)entity).fte$getText().getString());displays.add(d);
            }
        }
        row.addProperty("world_entity_count",entityCount);row.add("text_displays",displays);
        var npcs=new JsonArray();for(var player:world.players()) {
            var n=new JsonObject();n.addProperty("name",VersionAccess.profileName(player.getGameProfile()));n.addProperty("x",player.blockPosition().getX());
            n.addProperty("y",player.blockPosition().getY());n.addProperty("z",player.blockPosition().getZ());n.addProperty("invisible",player.isInvisible());npcs.add(n);
        }
        row.add("world_players",npcs);
        var chunks=new JsonArray();var chests=new JsonArray();for(var item:fixture().getAsJsonArray("chests")) {
            var a=item.getAsJsonObject().getAsJsonArray("pos");var pos=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
            var chunk=new JsonObject();chunk.addProperty("x",pos.getX()>>4);chunk.addProperty("z",pos.getZ()>>4);
            chunk.addProperty("loaded",world.hasChunkAt(pos));chunk.addProperty("kind",world.getChunkAt(pos).getClass().getSimpleName());chunks.add(chunk);
            chunk.addProperty("empty",world.getChunkAt(pos) instanceof net.minecraft.world.level.chunk.EmptyLevelChunk);
            var block=world.getBlockState(pos).getBlock();
            chests.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        row.add("chest_chunks",chunks);
        row.add("chunk_events",chunkEvents.deepCopy());
        row.add("blocks",chests);System.out.println("[PHASE3] WORLD_SNAPSHOT "+row);
    }
    private void finish(String status,String error) {
        if(finished)return;finished=true;
        var loader=FabricLoader.getInstance();var output=new JsonObject();
        output.addProperty("status",status);output.addProperty("error",error);output.addProperty("target",System.getProperty("fte.phase3.minecraft"));
        output.addProperty("sdk_version",loader.getModContainer("fte-multiversion-poc").orElseThrow().getMetadata().getVersion().getFriendlyString());
        output.addProperty("java",Runtime.version().feature());output.addProperty("namespace",loader.getMappingResolver().getCurrentRuntimeNamespace());
        output.addProperty("java_vendor",System.getProperty("java.vendor"));output.addProperty("java_runtime",System.getProperty("java.runtime.version"));
        output.addProperty("java_home",System.getProperty("java.home"));
        output.addProperty("fixture_revision",sessions.get(Math.max(0,session-1)).getAsJsonObject().get("fixture_revision").getAsInt());
        output.addProperty("development",loader.isDevelopmentEnvironment());output.addProperty("origin",loader.getModContainer("fte-multiversion-poc").orElseThrow().getOrigin().getKind().name());
        output.addProperty("joins",phase4flags?observedJoins:runtime.bans().joins());output.addProperty("disconnects",phase4flags?observedDisconnects:runtime.bans().disconnects());output.addProperty("periodic",phase4flags?totalPeriodic:runtime.periodic());
        output.addProperty("individual_flags",phase4flags);
        output.addProperty("stale_disconnects",phase4a||phase4online?net.funtimeevents.bootstrap.Bootstrap.getInstance().platform().staleDisconnects():TrackerSliceAPI.staleDisconnects());
        output.addProperty("handoff_empty",phase4a||phase4online?net.funtimeevents.bootstrap.Bootstrap.getInstance().platform().handoffEmpty():TrackerSliceAPI.handoffEmpty());
        output.addProperty("disconnect_deferred_ticks",disconnectDeferredTicks);
        output.addProperty("phase4a",phase4a);
        if(network!=null){output.add("public_network",network.result);network.close();}
        if(phase4a||phase4online) { net.funtimeevents.api.FunTimeEventsAPI.shutdown();
            require(net.funtimeevents.bootstrap.Bootstrap.getInstance().runtime()==null,"Shutdown retained runtime"); }
        output.add("sessions",results);output.add("callbacks",callbacks);output.add("payloads",payloads);output.add("performance",GsonHolder.INSTANCE.toJsonTree(TrackerMetrics.scans()));
        output.add("callback_performance",GsonHolder.INSTANCE.toJsonTree(callbackPerformance));
        if(measureThreadCpu)output.add("cpu_and_wall_spans",cpuAndWallSpans);
        if("c".equals(System.getProperty("fte.phase4.stage"))) {
            java.util.concurrent.CompletableFuture.runAsync(()->{
                long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                List<String> remaining;
                do {
                    remaining=Thread.getAllStackTraces().keySet().stream().filter(t->t.isAlive()&&t.getName().startsWith("FTE-"))
                        .map(t->t.getName()+"#"+t.threadId()).sorted().toList();
                    if(remaining.isEmpty())break;
                    try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                }while(System.nanoTime()<until);
                output.addProperty("shutdown_threads_empty",remaining.isEmpty());output.add("shutdown_remaining_threads",GsonHolder.INSTANCE.toJsonTree(remaining));
                if(!remaining.isEmpty()){output.addProperty("status","FAIL");output.addProperty("error","SDK threads remained after shutdown: "+remaining);}
                writeResult(output);
            }).whenComplete((v,writeFailure)->{if(writeFailure!=null)writeFailure.printStackTrace();Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());});
        } else {writeResult(output);Minecraft.getInstance().stop();}
    }
    private void writeResult(JsonObject output) {
        try{Files.writeString(Path.of("phase3-result.json"),GsonHolder.INSTANCE.toJson(output),StandardCharsets.UTF_8);}
        catch(IOException e){throw new IllegalStateException("Result write failed",e);}
        System.out.println("[PHASE3] "+output.get("status").getAsString()+" sessions="+results.size()+" payloads="+payloads.size());
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
