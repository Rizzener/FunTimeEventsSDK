package com.funtimeevents.sdk.scheduler;

public final class Scheduler {

    private final int intervalTicks;
    private int tickCounter;
    private volatile boolean running;
    private volatile Runnable task;

    public Scheduler(int intervalTicks) {
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    public void start(Runnable task) {
        if (running) {
            return;
        }
        this.task = task;
        this.tickCounter = 0;
        this.running = true;
    }

    public void tick() {
        if (!running || task == null) {
            return;
        }
        tickCounter++;
        if (tickCounter >= intervalTicks) {
            tickCounter = 0;
            task.run();
        }
    }

    public void stop() {
        running = false;
        task = null;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }
}
