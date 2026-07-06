package staraxis.game.server;

import staraxis.game.StarAxisGameRuntime;

/**
 * TickLoop（独立 Tick 循环）。
 *
 * 以固定速率驱动 StarAxisGameRuntime.update()，不绑定渲染帧率。
 * 支持暂停、继续、停止和状态查询。
 * 作为 Minecraft Server 模式的 Tick 循环。
 */
public class TickLoop {

    private final StarAxisGameRuntime runtime;
    private final int ticksPerSecond;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private Thread loopThread;
    private long tickCount = 0;
    private long lastStatsTime = 0;
    private int statsIntervalTicks = 0;
    private long startNanos;

    private static final int STATS_INTERVAL_SECONDS = 10;

    public TickLoop(StarAxisGameRuntime runtime, int ticksPerSecond) {
        this.runtime = runtime;
        this.ticksPerSecond = ticksPerSecond;
    }

    /**
     * 启动 Tick 循环（阻塞当前线程）。
     */
    public void start() {
        if (loopThread != null) {
            System.out.println("[TickLoop] \u5df2\u5728\u8fd0\u884c\u4e2d");
            return;
        }
        running = true;
        paused = false;
        startNanos = System.nanoTime();
        loopThread = Thread.currentThread();
        System.out.println("[TickLoop] \u542f\u52a8 \u2502 TPS=" + ticksPerSecond);

        tickLoop();
    }

    /**
     * 在独立线程中启动 Tick 循环。
     */
    public void startAsync() {
        Thread t = new Thread(this::start, "game-tick-loop");
        t.setDaemon(false);
        t.start();
    }

    /**
     * 停止 Tick 循环。
     */
    public void stop() {
        running = false;
        loopThread = null;
    }

    /**
     * 暂停/继续。
     */
    public void setPaused(boolean p) {
        this.paused = p;
        if (p) {
            System.out.println("[TickLoop] \u6682\u505c");
        } else {
            System.out.println("[TickLoop] \u7ee7\u7eed");
        }
    }

    public boolean isPaused() { return paused; }
    public boolean isRunning() { return running; }
    public long getTickCount() { return tickCount; }

    /**
     * 核心循环：固定速率驱动 tick。
     */
    private void tickLoop() {
        long nanosPerTick = 1_000_000_000L / ticksPerSecond;
        long nextTickNanos = System.nanoTime();

        while (running) {
            long now = System.nanoTime();

            if (paused) {
                // 暂停时休息 100ms 避免忙等
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                nextTickNanos = System.nanoTime() + nanosPerTick;
                continue;
            }

            if (now < nextTickNanos) {
                long sleepMs = (nextTickNanos - now) / 1_000_000L;
                if (sleepMs > 0) {
                    try { Thread.sleep(sleepMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
                continue;
            }

            // 执行一个 Tick
            float dtSeconds = 1.0f / ticksPerSecond;
            runtime.update(dtSeconds);
            tickCount++;
            statsIntervalTicks++;

            // 定期输出统计
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000_000L;
            if (elapsed - lastStatsTime >= STATS_INTERVAL_SECONDS) {
                printStats(elapsed);
                lastStatsTime = elapsed;
                statsIntervalTicks = 0;
            }

            // 推进到下一个 tick 目标时间
            nextTickNanos += nanosPerTick;

            // 如果落后太多（超过 1 秒），跳过多余 tick 追赶
            if (System.nanoTime() - nextTickNanos > 1_000_000_000L) {
                nextTickNanos = System.nanoTime();
            }
        }

        System.out.println("[TickLoop] \u505c\u6b62 \u2502 \u5171\u6267\u884c " + tickCount + " ticks");
    }

    private void printStats(long elapsedSeconds) {
        long entityCount = 0;
        long systemCount = 0;
        long simTick = 0;
        try {
            var ws = runtime.getWorldStateForSimOnly();
            entityCount = ws.entitiesById.size();
            systemCount = ws.astro.getSystemsView().size();
            simTick = ws.time.simulationTick;
        } catch (Exception ignored) {}

        double avgTps = elapsedSeconds > 0 ? (double) tickCount / elapsedSeconds : 0;
        System.out.printf("[TickLoop] \u72b6\u6001 \u2502 Tick=%d \u2502 TPS=%.1f \u2502 \u5b9e\u4f53=%d \u2502 \u6052\u661f\u7cfb=%d \u2502 \u5df2\u8fd0\u884c=%ds%n",
            simTick, avgTps, entityCount, systemCount, elapsedSeconds);
    }
}
