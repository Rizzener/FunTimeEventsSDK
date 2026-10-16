package net.funtimeevents.poc.core;

import net.funtimeevents.internal.game.DisconnectHandoff;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;

public final class DisconnectStressCheck {
    public static void main(String[] args) throws Exception {
        lateDisconnectAfterNewJoin();
        var handoff = new DisconnectHandoff<Object>();
        int[] count = {0};
        Runnable stopped = () -> count[0]++;
        Object previous = new Object();
        handoff.join(previous, stopped);
        for (int i = 0; i < 10_000; i++) {
            handoff.offer(previous); handoff.offer(previous);
            Object next = new Object();
            handoff.join(next, stopped);
            handoff.offer(previous); // Late callback after the new connection.
            handoff.drain(stopped);
            previous = next;
        }
        if (count[0] != 10_000) throw new AssertionError("Reconnect / duplicate delivery");
        Object current = previous;
        Object stale = new Object();
        var start = new CountDownLatch(1);
        Thread[] producers = new Thread[4];
        for (int p = 0; p < producers.length; p++) {
            producers[p] = new Thread(() -> {
                try { start.await(); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                for (int i = 0; i < 50_000; i++) handoff.offer(stale);
            }, "Netty-synthetic-" + p);
            producers[p].start();
        }
        start.countDown();
        boolean alive;
        do {
            handoff.drain(stopped);
            alive = false;
            for (Thread producer : producers) alive |= producer.isAlive();
        } while (alive);
        for (Thread producer : producers) producer.join();
        handoff.drain(stopped);
        if (count[0] != 10_000) throw new AssertionError("Stale callback stopped current session");
        handoff.offer(current); handoff.offer(current); handoff.drain(stopped);
        if (count[0] != 10_001 || !handoff.empty() || handoff.stale() != 220_001) throw new AssertionError("Totals / retained queue");
        for (int i = 0; i < 100_000; i++) handoff.drain(stopped);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 1_000_000; i++) handoff.drain(stopped);
        long bytes = bean.getThreadAllocatedBytes(id) - before;
        if (bytes > 10_000) throw new AssertionError("Empty polling allocation: " + bytes);
        System.out.println("[PHASE3-HANDOFF] PASS java="+Runtime.version().feature()+" joins=10001 stale=" + handoff.stale()
            + " producers=4 concurrent_offers=200000 empty_polls=1000000 allocated_bytes=" + bytes);
    }

    private static void lateDisconnectAfterNewJoin() {
        final class Access implements net.funtimeevents.internal.game.ServerAccess {
            String host="old.funtime.su", title="Анархия-104";
            public String serverAddress(){return host;}
            public String sidebarTitle(){return title;}
        }
        var access=new Access();
        var runtime=new net.funtimeevents.internal.game.GameSessionRuntime(access,payload->{throw new AssertionError("Unexpected payload");});
        var handoff=new DisconnectHandoff<Object>();
        Object old=new Object(), next=new Object();Runnable stopped=runtime::disconnected;
        handoff.join(old,stopped);runtime.joined();
        access.host="new.funtime.sh";access.title=null;
        // Deliberately deliver the new JOIN before the old Netty callback.
        handoff.join(next,stopped);runtime.joined();
        if(runtime.joins()!=2 || runtime.disconnects()!=1 || runtime.state().serverId()!=-1)
            throw new AssertionError("New JOIN before old DISCONNECT retained old session: joins="+runtime.joins()+" disconnects="+runtime.disconnects()+" state="+runtime.state());
        handoff.offer(old);handoff.offer(old);handoff.drain(stopped);
        if(!runtime.isJoined()||runtime.disconnects()!=1)throw new AssertionError("Late callback stopped new session");
        handoff.offer(next);handoff.drain(stopped);
        if(runtime.disconnects()!=2||runtime.state().serverIp()!=null)throw new AssertionError("Final cleanup");
        System.out.println("[PHASE3-LATE-JOIN] PASS new_join_before_old_disconnect=true state_cleared=true late_old_ignored=true");
    }
}
