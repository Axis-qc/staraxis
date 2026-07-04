package staraxis.game.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LptAssigner（LPT 加权贪心分配算法）。
 *
 * 按恒星系实体数降序排列，每次把当前最重的星系分配给总负载最轻的线程。
 * 保证：最忙线程的负载 ≤ 最优值 + 单个最大星系负载。
 *
 * 用于 Tick 流水线阶段2，将星系分配到 Worker 线程。
 */
public final class LptAssigner {

    /** 冷星系打包权重（实体数 < HOT_THRESHOLD 的星系按此值计入）。 */
    public static final int COLD_SYSTEM_WEIGHT = 1;

    /** 热星系判定的实体数下限。 */
    public static final int HOT_THRESHOLD = 50;

    private LptAssigner() {}

    /**
     * 执行 LPT 分配。
     *
     * @param systems     所有星系的负载列表（将被复制，不修改原始数据）
     * @param threadCount Worker 线程数（>= 1）
     * @return 分配映射：threadId（0-based）→ 该线程负责的 systemId 列表
     */
    public static Map<Integer, List<Long>> assign(List<SystemLoad> systems, int threadCount) {
        if (threadCount < 1) threadCount = 1;
        if (systems == null || systems.isEmpty()) return Map.of();

        // 初始化负载桶
        long[] loads = new long[threadCount];
        Map<Integer, List<Long>> assignment = new HashMap<>();
        for (int i = 0; i < threadCount; i++) {
            assignment.put(i, new ArrayList<>());
        }

        // 按实体数降序排列（热星系优先分配）
        List<SystemLoad> sorted = new ArrayList<>(systems);
        sorted.sort((a, b) -> Integer.compare(b.effectiveWeight(), a.effectiveWeight()));

        for (SystemLoad sys : sorted) {
            int bestThread = argMin(loads);
            assignment.get(bestThread).add(sys.systemId);
            loads[bestThread] += sys.effectiveWeight();
        }

        return assignment;
    }

    /**
     * 快速增量分配：将单个热星系分配给负载最轻的线程。
     * 用于动态重平衡。
     */
    public static int assignHotSystem(SystemLoad system, long[] currentLoads) {
        int bestThread = argMin(currentLoads);
        currentLoads[bestThread] += system.effectiveWeight();
        return bestThread;
    }

    /**
     * 找到负载最轻的线程索引。
     */
    private static int argMin(long[] loads) {
        int best = 0;
        for (int i = 1; i < loads.length; i++) {
            if (loads[i] < loads[best]) best = i;
        }
        return best;
    }
}
