package com.funtimeevents.sdk.net;

import com.funtimeevents.sdk.event.EventBus;
import com.funtimeevents.sdk.event.FteEvent;
import com.funtimeevents.sdk.util.FteLogger;

import java.util.List;

public final class BatchSender {

    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_MAX_BATCH_SIZE = 100;
    private static final int MAX_BACKOFF_SECONDS = 60;

    private final ApiClient apiClient;
    private final int intervalSeconds;
    private final int maxBatchSize;
    private volatile boolean running;
    private Thread thread;

    public BatchSender(ApiClient apiClient) {
        this(apiClient, DEFAULT_INTERVAL_SECONDS, DEFAULT_MAX_BATCH_SIZE);
    }

    public BatchSender(ApiClient apiClient, int intervalSeconds, int maxBatchSize) {
        this.apiClient = apiClient;
        this.intervalSeconds = intervalSeconds;
        this.maxBatchSize = maxBatchSize;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "FTE-BatchSender");
        thread.setDaemon(true);
        thread.start();
        FteLogger.info("Background batch sender started (interval: " + intervalSeconds + "s)");
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void runLoop() {
        int retryBackoff = 1;
        while (running) {
            try {
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                break;
            }

            if (!running) {
                break;
            }

            try {
                List<FteEvent> batch = EventBus.getInstance().drain(maxBatchSize);
                if (batch.isEmpty()) {
                    retryBackoff = 1;
                    continue;
                }

                apiClient.sendEvents(batch).get();
                retryBackoff = 1;
            } catch (Exception e) {
                FteLogger.warn("Batch send failed, retrying in " + retryBackoff + "s: " + e.getMessage());
                try {
                    Thread.sleep(retryBackoff * 1000L);
                } catch (InterruptedException ie) {
                    break;
                }
                retryBackoff = Math.min(retryBackoff * 2, MAX_BACKOFF_SECONDS);
            }
        }
        FteLogger.info("Background batch sender stopped");
    }
}
