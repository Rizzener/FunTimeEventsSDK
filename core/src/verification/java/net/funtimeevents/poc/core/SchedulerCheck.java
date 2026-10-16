package net.funtimeevents.poc.core;
import net.funtimeevents.scheduler.Scheduler;

public final class SchedulerCheck {
    public static void main(String[] args) {
        var scheduler=new Scheduler(200);int[] calls={0};Runnable task=()->calls[0]++;
        scheduler.start(task);scheduler.start(()->{throw new AssertionError("Duplicate start replaced task");});
        for(int i=0;i<199;i++)scheduler.tick();
        if(calls[0]!=0)throw new AssertionError("Too early");
        scheduler.tick();if(calls[0]!=1)throw new AssertionError("Wrong cadence");
        for(int i=0;i<17;i++)scheduler.tick();scheduler.stop();
        for(int i=0;i<500;i++)scheduler.tick();if(calls[0]!=1)throw new AssertionError("Emitted after stop");
        scheduler.start(task);for(int i=0;i<199;i++)scheduler.tick();
        if(calls[0]!=1)throw new AssertionError("Previous counter survived session");
        scheduler.tick();if(calls[0]!=2)throw new AssertionError("Reconnect cadence");
        var minimum=new Scheduler(0);minimum.start(task);minimum.tick();
        if(calls[0]!=3||minimum.getIntervalTicks()!=1)throw new AssertionError("Clamp changed");
        System.out.println("[PHASE3-SCHEDULER] PASS interval=200 reset=true idempotent=true clamp=1");
    }
}
