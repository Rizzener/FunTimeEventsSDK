package net.funtimeevents.internal.tracker;

import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in internal diagnostics. Disabled path does not allocate or read the clock. */
public final class TrackerMetrics {
    private static final boolean ENABLED = Boolean.getBoolean("fte.diagnostics.metrics");
    private static final Map<String, long[]> SCANS = new LinkedHashMap<>();
    private static long entities, displays, blocks;
    private TrackerMetrics() {}
    public static long begin() { return ENABLED ? System.nanoTime() : 0; }
    public static void end(String tracker, long started) {
        if (!ENABLED) return;
        long elapsed = System.nanoTime() - started;
        long[] stat = SCANS.computeIfAbsent(tracker, key -> new long[3]);
        stat[0]++; stat[1] += elapsed; stat[2] = Math.max(stat[2], elapsed);
    }
    public static void entityVisited() { if (ENABLED) entities++; }
    public static void displayRead() { if (ENABLED) displays++; }
    public static void blockRead() { if (ENABLED) blocks++; }
    public static long entities() { return entities; }
    public static long displays() { return displays; }
    public static long blocks() { return blocks; }
    public static Map<String, long[]> scans() { return Map.copyOf(SCANS); }
}
