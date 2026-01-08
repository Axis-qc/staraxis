package com.staraxis.render.universe;

/** Utility to measure and accumulate jump durations for performance tuning. */
public final class JumpTimer {
    private long startNs;
    private double lastDurationMs;

    public void start() {
        startNs = System.nanoTime();
    }

    public double stop() {
        lastDurationMs = (System.nanoTime() - startNs) / 1_000_000.0;
        return lastDurationMs;
    }

    public double getLastDurationMs() {
        return lastDurationMs;
    }
}
