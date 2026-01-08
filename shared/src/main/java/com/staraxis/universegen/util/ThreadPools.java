package com.staraxis.universegen.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ThreadPools {
    private static final int CPU = Runtime.getRuntime().availableProcessors();

    private ThreadPools() {}

    public static ExecutorService generationPool() {
        return Executors.newWorkStealingPool(CPU);
    }

    public static ExecutorService ioPool() {
        return Executors.newFixedThreadPool(Math.max(2, CPU / 2));
    }

    public static void shutdown(ExecutorService pool) {
        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
