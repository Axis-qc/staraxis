package staraxis.game.log;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PerformanceMonitor（性能监测器）喵。
 *
 * 作用喵：
 * - 记录游戏运行时的性能指标喵。
 * - 收集最近 60 秒、30 秒、10 秒的统计数据喵。
 * - 定期输出性能报告到 gamedata/logs/performance.log 喵。
 *
 * 监测指标喵：
 * - Tick 执行时间（totalTickTimeMs）喵
 * - 快照生成时间（snapshotBuildTimeMs）喵
 * - 实体总数（entityCount）喵
 * - 星区总数（sectorCount）喵
 * - 活跃玩家数（activePlayerCount）喵
 * - 内存使用情况（memoryUsageMB）喵
 */
public class PerformanceMonitor {

    private static final Path LOG_PATH = Path.of("gamedata/logs/performance.log");

    private static volatile PrintWriter out;
    private static volatile boolean inited = false;

    /** 可读时间格式化器喵。 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    /** 性能数据队列（保存最近 60 秒的数据）喵。 */
    private final ConcurrentLinkedQueue<PerformanceSnapshot> snapshots = new ConcurrentLinkedQueue<>();

    /** 最近一次快照生成时间（毫秒）喵。 */
    private final AtomicLong lastSnapshotBuildTimeMs = new AtomicLong(0);

    /** 队列最大容量（60 秒，每秒约 25 个 tick）喵。 */
    private static final int MAX_QUEUE_SIZE = 1500;

    /** 上次报告时间戳喵。 */
    private final AtomicLong lastReportTime = new AtomicLong(0);

    /** 报告间隔（10 秒）喵。 */
    private static final long REPORT_INTERVAL_MS = 10_000L;

    private static PerformanceMonitor instance;

    /**
     * 性能快照数据类喵。
     */
    public static class PerformanceSnapshot {
        public final long timestamp;
        public final long tickTimeMs;
        public final long snapshotBuildTimeMs;
        public final int entityCount;
        public final int sectorCount;
        public final int activePlayerCount;
        public final long memoryUsageMB;
        public final long simulationTick;

        public PerformanceSnapshot(long tickTimeMs, long snapshotBuildTimeMs, int entityCount,
                int sectorCount, int activePlayerCount, long memoryUsageMB, long simulationTick) {
            this.timestamp = System.currentTimeMillis();
            this.tickTimeMs = tickTimeMs;
            this.snapshotBuildTimeMs = snapshotBuildTimeMs;
            this.entityCount = entityCount;
            this.sectorCount = sectorCount;
            this.activePlayerCount = activePlayerCount;
            this.memoryUsageMB = memoryUsageMB;
            this.simulationTick = simulationTick;
        }
    }

    /**
     * 统计数据类喵。
     */
    public static class Statistics {
        public final String period;
        public final int sampleCount;
        public final double avgTickTimeMs;
        public final double maxTickTimeMs;
        public final double minTickTimeMs;
        public final double avgSnapshotBuildTimeMs;
        public final double maxSnapshotBuildTimeMs;
        public final double avgEntityCount;
        public final double avgMemoryUsageMB;
        public final double currentMemoryUsageMB;

        public Statistics(String period, int sampleCount, double avgTickTimeMs, double maxTickTimeMs,
                double minTickTimeMs, double avgSnapshotBuildTimeMs, double maxSnapshotBuildTimeMs,
                double avgEntityCount, double avgMemoryUsageMB, double currentMemoryUsageMB) {
            this.period = period;
            this.sampleCount = sampleCount;
            this.avgTickTimeMs = avgTickTimeMs;
            this.maxTickTimeMs = maxTickTimeMs;
            this.minTickTimeMs = minTickTimeMs;
            this.avgSnapshotBuildTimeMs = avgSnapshotBuildTimeMs;
            this.maxSnapshotBuildTimeMs = maxSnapshotBuildTimeMs;
            this.avgEntityCount = avgEntityCount;
            this.avgMemoryUsageMB = avgMemoryUsageMB;
            this.currentMemoryUsageMB = currentMemoryUsageMB;
        }
    }

    private PerformanceMonitor() {
        init();
    }

    public static synchronized PerformanceMonitor getInstance() {
        if (instance == null) {
            instance = new PerformanceMonitor();
        }
        return instance;
    }

    private static synchronized void init() {
        if (inited) {
            return;
        }
        inited = true;

        try {
            Files.createDirectories(LOG_PATH.getParent());
            out = new PrintWriter(new FileWriter(LOG_PATH.toFile(), false), true);
            out.println("[" + fmtTs(System.currentTimeMillis()) + "] PerformanceMonitor initialized 喵");
            out.println("=".repeat(100));
        } catch (Exception e) {
            System.err.println("[PerformanceMonitor] Failed to initialize log: " + e.getMessage());
            out = null;
        }
    }

    private static String fmtTs(long ms) {
        return TS.format(Instant.ofEpochMilli(ms));
    }

    /**
     * 更新最近一次快照生成时间喵。
     * 由 SnapshotBroadcaster 在生成快照后调用喵。
     *
     * @param buildTimeMs 快照生成耗时（毫秒）
     */
    public void updateLastSnapshotBuildTime(long buildTimeMs) {
        lastSnapshotBuildTimeMs.set(buildTimeMs);
    }

    /**
     * 记录一次性能快照喵。
     */
    public void record(long tickTimeMs, long snapshotBuildTimeMs, int entityCount,
            int sectorCount, int activePlayerCount, long simulationTick) {

        long memoryUsageMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

        // 使用最近一次记录的快照生成时间（如果 snapshotBuildTimeMs 为 0）喵
        long actualSnapshotBuildTimeMs = snapshotBuildTimeMs > 0 ?
                snapshotBuildTimeMs : lastSnapshotBuildTimeMs.getAndSet(0);

        PerformanceSnapshot snapshot = new PerformanceSnapshot(
                tickTimeMs, actualSnapshotBuildTimeMs, entityCount, sectorCount,
                activePlayerCount, memoryUsageMB, simulationTick);

        snapshots.offer(snapshot);

        // 保持队列大小在限制内喵
        while (snapshots.size() > MAX_QUEUE_SIZE) {
            snapshots.poll();
        }

        // 检查是否需要生成报告喵
        long now = System.currentTimeMillis();
        long lastReport = lastReportTime.get();
        if (now - lastReport >= REPORT_INTERVAL_MS && lastReportTime.compareAndSet(lastReport, now)) {
            generateReport();
        }
    }

    /**
     * 生成性能报告喵。
     */
    public void generateReport() {
        if (out == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // 计算各时间段统计喵
        Statistics stats10s = calculateStatistics(now - 10_000, "10s");
        Statistics stats30s = calculateStatistics(now - 30_000, "30s");
        Statistics stats60s = calculateStatistics(now - 60_000, "60s");

        // 打印报告喵
        synchronized (out) {
            out.println();
            out.println("[" + fmtTs(now) + "] ====== Performance Report ====== 喵");
            out.println();

            printStatistics(stats10s);
            printStatistics(stats30s);
            printStatistics(stats60s);

            out.println("=".repeat(100));
            out.flush();
        }
    }

    private void printStatistics(Statistics stats) {
        out.printf("  [%s] Samples: %d | Tick: avg=%.2fms max=%.2fms min=%.2fms | " +
                "Snapshot: avg=%.2fms max=%.2fms | Entities: avg=%.1f | Memory: avg=%.1fMB current=%.1fMB 喵%n",
                stats.period,
                stats.sampleCount,
                stats.avgTickTimeMs,
                stats.maxTickTimeMs,
                stats.minTickTimeMs,
                stats.avgSnapshotBuildTimeMs,
                stats.maxSnapshotBuildTimeMs,
                stats.avgEntityCount,
                stats.avgMemoryUsageMB,
                stats.currentMemoryUsageMB);
    }

    private Statistics calculateStatistics(long cutoffTime, String period) {
        List<PerformanceSnapshot> recent = new ArrayList<>();

        for (PerformanceSnapshot snap : snapshots) {
            if (snap.timestamp >= cutoffTime) {
                recent.add(snap);
            }
        }

        int count = recent.size();
        if (count == 0) {
            return new Statistics(period, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        double sumTickTime = 0;
        double maxTickTime = 0;
        double minTickTime = Double.MAX_VALUE;
        double sumSnapshotTime = 0;
        double maxSnapshotTime = 0;
        double sumEntityCount = 0;
        double sumMemory = 0;
        double currentMemory = recent.get(recent.size() - 1).memoryUsageMB;

        for (PerformanceSnapshot snap : recent) {
            sumTickTime += snap.tickTimeMs;
            maxTickTime = Math.max(maxTickTime, snap.tickTimeMs);
            minTickTime = Math.min(minTickTime, snap.tickTimeMs);
            sumSnapshotTime += snap.snapshotBuildTimeMs;
            maxSnapshotTime = Math.max(maxSnapshotTime, snap.snapshotBuildTimeMs);
            sumEntityCount += snap.entityCount;
            sumMemory += snap.memoryUsageMB;
        }

        return new Statistics(
                period,
                count,
                sumTickTime / count,
                maxTickTime,
                minTickTime == Double.MAX_VALUE ? 0 : minTickTime,
                sumSnapshotTime / count,
                maxSnapshotTime,
                sumEntityCount / count,
                sumMemory / count,
                currentMemory
        );
    }

    /**
     * 强制生成当前性能报告喵。
     */
    public void forceReport() {
        long now = System.currentTimeMillis();
        lastReportTime.set(now);
        generateReport();
    }

    public static synchronized void close() {
        if (out != null) {
            try {
                out.flush();
                out.close();
            } catch (Exception ignored) {
            }
            out = null;
        }
        inited = false;
        instance = null;
    }
}
