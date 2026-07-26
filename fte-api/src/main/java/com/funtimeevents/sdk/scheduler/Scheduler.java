package com.funtimeevents.sdk.scheduler;

public final class Scheduler {

    private static final int DEFAULT_INTERVAL_TICKS = 200;

    private final int intervalTicks;
    private int tickCounter;
    private boolean running;
    private Runnable task;

    public Scheduler() {
        this(DEFAULT_INTERVAL_TICKS);
    }

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

    public boolean isRunning() {
        return running;
    }
}
