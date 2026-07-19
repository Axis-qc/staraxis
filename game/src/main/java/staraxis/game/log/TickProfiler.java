/*
 * TickProfiler
 *
 * 文件作用：
 * - 简单的逐阶段耗时记录器，输出到 gamedata/logs/perf_tick.log。
 * - 每个 game tick 记录各阶段耗时，每秒输出一次汇总。
 * - 用于定位性能瓶颈。
 *
 * 使用方式：
 * - TickProfiler.begin(Phase.XXX) / end() 包裹要测量的代码段。
 * - TickProfiler.logTick() 在每 tick 结束时调用。
 *
 * 注意事项：
 * - 线程不安全，仅在 game 主线程使用。
 * - 只在 debug 模式下启用，正式构建可移除。
 */

package staraxis.game.log;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * TickProfiler（逐阶段耗时记录器）。
 *
 * 测量每个 game tick 中各阶段的耗时，每秒输出到 gamedata/logs/perf_tick.log。
 */
public class TickProfiler {

    /** 测量阶段枚举。 */
    public enum Phase {
        TIMELINE("timeline"),
        ARRIVALS("arrivals"),
        OCTREE("octree"),
        LOAD_BALANCE("loadBalance"),
        COMMAND("command"),
        MOVEMENT("movement"),
        SNAPSHOT("snapshot");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Path LOG_PATH = Path.of("gamedata/logs/perf_tick.log");
    private static PrintWriter out;
    /** 每秒输出一次，避免刷屏。 */
    private static long lastLogTime;
    /** 当前 tick 的各阶段时间。 */
    private static long tickStartNs;
    private static Phase currentPhase;
    private static long currentPhaseStartNs;
    /** 10 秒统计。 */
    private static long totalTickTimeMs;
    private static int tickCount;
    private static long maxTickMs;
    /** 各阶段累计。 */
    private static long timelineMs, arrivalsMs, octreeMs, loadBalanceMs;
    private static long commandMs, movementMs, snapshotMs;
    private static final int FLUSH_INTERVAL_MS = 10000;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    public static synchronized void init() {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            if (out != null) out.close();
            out = new PrintWriter(new FileWriter(LOG_PATH.toFile(), false), true);
            out.println("[" + fmtTs() + "] TickProfiler initialized");
            out.println("tick,totalMs,timeline,arrivals,octree,loadBal,command,movement,snapshot,other,entityCount");
            lastLogTime = System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("[TickProfiler] init failed: " + e.getMessage());
        }
    }

    public static void begin(Phase phase) {
        currentPhase = phase;
        currentPhaseStartNs = System.nanoTime();
    }

    public static void end() {
        if (currentPhase == null) return;
        long elapsedNs = System.nanoTime() - currentPhaseStartNs;
        long elapsedMs = elapsedNs / 1_000_000L;

        switch (currentPhase) {
            case TIMELINE -> timelineMs += elapsedMs;
            case ARRIVALS -> arrivalsMs += elapsedMs;
            case OCTREE -> octreeMs += elapsedMs;
            case LOAD_BALANCE -> loadBalanceMs += elapsedMs;
            case COMMAND -> commandMs += elapsedMs;
            case MOVEMENT -> movementMs += elapsedMs;
            case SNAPSHOT -> snapshotMs += elapsedMs;
        }

        currentPhase = null;
    }

    /**
     * 记录客户端渲染耗时（由 ClientGame 每 ~60 帧调用一次）。
     */
    public static void logRender(long updateNs, long renderNs) {
        if (out == null) return;
        synchronized (out) {
            out.println(String.format("RENDER,%s,update=%.1fms,render=%.1fms",
                fmtTs(), updateNs / 1_000_000.0, renderNs / 1_000_000.0));
            out.flush();
        }
    }

    public static void tickStart() {
        tickStartNs = System.nanoTime();
    }

    public static void tickEnd(int entityCount) {
        long tickNs = System.nanoTime() - tickStartNs;
        long tickMs = tickNs / 1_000_000L;
        totalTickTimeMs += tickMs;
        tickCount++;
        if (tickMs > maxTickMs) maxTickMs = tickMs;

        // 每秒输出
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= 1000 && out != null) {
            // 输出所有阶段的累计，自动计算 snapshot 等缺失阶段
            long accounted = timelineMs + arrivalsMs + octreeMs + loadBalanceMs + commandMs + movementMs + snapshotMs;
            String line = String.format("%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                fmtTs(), tickMs, totalTickTimeMs,
                timelineMs, arrivalsMs, octreeMs, loadBalanceMs,
                commandMs, movementMs, snapshotMs,
                totalTickTimeMs - accounted, entityCount);
            synchronized (out) {
                out.println(line);
                out.flush();
            }
            lastLogTime = now;

            // 每 10 秒输出一条带最大值的
            if (now % FLUSH_INTERVAL_MS < 1000) {
                out.println("# 10s summary: count=" + tickCount + " avg=" + (totalTickTimeMs/tickCount) + "ms max=" + maxTickMs + "ms");
                totalTickTimeMs = 0;
                tickCount = 0;
                maxTickMs = 0;
                timelineMs = arrivalsMs = octreeMs = loadBalanceMs = 0;
                commandMs = movementMs = snapshotMs = 0;
            }
        }
    }

    private static String fmtTs() {
        return TS.format(Instant.now());
    }
}
