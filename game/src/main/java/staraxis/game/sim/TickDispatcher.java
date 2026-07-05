package staraxis.game.sim;

import staraxis.game.state.WorldState;
import staraxis.game.space.event.CrossSystemEvent;
import staraxis.game.space.event.CrossSystemEventTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TickDispatcher（Tick 分派器）。
 *
 * 管理单 tick 的 5 阶段流水线调度：
 *   阶段1: 处理到期跨系统事件（到达）
 *   阶段2: LPT 分配 + 重平衡检查
 *   阶段3: per-System 并行计算
 *   阶段4: 合并新事件
 *   阶段5: 发布快照
 *
 * 当前默认单线程模式（Worker 数 = 1），所有阶段在主线程顺序执行。
 * 后续设置 workerCount > 1 可激活多线程阶段3（无需改动流水线结构）。
 */
public class TickDispatcher {

    /** 当前 Worker 线程数（1 = 单线程模式）。 */
    private int workerCount = 1;

    /** LPT 分配结果（systemId -> workerId），每 tick 更新。 */
    private Map<Long, Integer> currentAssignment = Map.of();

    /** Worker 本地缓冲区列表。 */
    private final java.util.ArrayList<WorkerLocalBuffer> workerBuffers = new ArrayList<>();

    /** 重平衡防抖：星系上次迁移的 tick。 */
    private final java.util.Map<Long, Long> lastMigrationTick = new java.util.HashMap<>();

    /** 两次迁移之间的最小间隔 tick。 */
    public static final long MIN_MIGRATION_INTERVAL = 300;

    public TickDispatcher() {
        // 默认创建 1 个 Worker 缓冲区（单线程模式）
        workerBuffers.add(new WorkerLocalBuffer(0));
    }

    // ── 配置 ──

    /**
     * 设置 Worker 线程数。
     * 1 = 单线程（默认），> 1 时激活多线程阶段3。
     * 每次设置会重建 Worker 缓冲区。
     */
    public synchronized void setWorkerCount(int count) {
        if (count < 1) count = 1;
        this.workerCount = count;
        workerBuffers.clear();
        for (int i = 0; i < count; i++) {
            workerBuffers.add(new WorkerLocalBuffer(i));
        }
    }

    public int getWorkerCount() {
        return workerCount;
    }

    // ── 流水线各阶段 ──

    /**
     * 阶段1：处理到期跨系统事件。
     * 将到达实体恢复到目标星系的 entityIdsBySystem 中。
     */
    public void stage1Arrivals(WorldState worldState, long currentTick) {
        CrossSystemEventTable table = worldState.crossSystemEventTable;
        List<CrossSystemEvent> dueEvents = table.getEventsDueAt(currentTick);

        for (CrossSystemEvent event : dueEvents) {
            var entity = worldState.entitiesById.get(event.entityId);
            if (entity == null) continue;

            entity.systemId = event.targetSystemId;
            worldState.entityIdsBySystem
                    .computeIfAbsent(event.targetSystemId, k -> new ArrayList<>())
                    .add(entity.entityId);
        }
    }

    /** Octree 重建间隔（tick 数），降低每 tick 全量重建开销。 */
    private static final int OCTREE_REBUILD_INTERVAL = 20; // 每秒重建一次（20tick/s）

    /**
     * 阶段1.5：重建 Octree 空间索引。
     * 每 OCTREE_REBUILD_INTERVAL tick 重建一次，降低开销。
     */
    public void stage1halfRebuildOctree(WorldState worldState) {
        if (worldState.time.simulationTick % OCTREE_REBUILD_INTERVAL != 0) {
            return;
        }
        worldState.galaxyOctree.rebuild(new ArrayList<>(worldState.entitiesById.values()));
    }

    /**
     * 阶段2：LPT 分配 + 收集 SystemLoad。
     * 单线程模式（workerCount=1）下跳过，无需分配。
     */
    public Map<Integer, List<Long>> stage2LoadBalance(WorldState worldState, long currentTick) {
        if (workerCount <= 1) {
            return Map.of(0, new ArrayList<>(worldState.entityIdsBySystem.keySet()));
        }

        List<SystemLoad> allLoads = new ArrayList<>();

        for (Map.Entry<Long, List<Long>> entry : worldState.entityIdsBySystem.entrySet()) {
            long systemId = entry.getKey();
            List<Long> ids = entry.getValue();
            if (ids == null || ids.isEmpty()) continue;

            SystemLoad load = new SystemLoad(systemId, ids.size());

            // 统计动态实体数
            int dynamic = 0;
            for (long eid : ids) {
                var entity = worldState.entitiesById.get(eid);
                if (entity != null && entity.entityType == staraxis.game.entity.EntityType.SHIP) {
                    dynamic++;
                }
            }
            load.dynamicCount = dynamic;
            load.lastActiveTick = dynamic > 0 ? currentTick : load.lastActiveTick;
            allLoads.add(load);
        }

        // 执行 LPT 分配
        Map<Integer, List<Long>> assignment = LptAssigner.assign(allLoads, workerCount);

        // 构建 systemId -> workerId 映射
        Map<Long, Integer> newAssignment = new java.util.HashMap<>();
        for (Map.Entry<Integer, List<Long>> entry : assignment.entrySet()) {
            int workerId = entry.getKey();
            for (long systemId : entry.getValue()) {
                newAssignment.put(systemId, workerId);
            }
        }
        this.currentAssignment = newAssignment;

        return assignment;
    }

    /**
     * 阶段3：per-System 计算（当前单线程，顺序遍历分配到的星系）。
     *
     * 在多线程模式下，每个 Worker 独立调用此方法处理自己的星系列表。
     *
     * @param worldState     世界状态
     * @param assignedSystems 该 Worker 负责的星系 ID 列表
     * @param buffer         该 Worker 的本地缓冲区（写入 pendingOut）
     * @param dtGameSeconds  本 tick 的游戏秒数
     */
    public void stage3PerSystemCalc(WorldState worldState, List<Long> assignedSystems,
                                    WorkerLocalBuffer buffer, double dtGameSeconds) {
        if (assignedSystems == null || assignedSystems.isEmpty()) return;

        // 按星系遍历所有实体
        // 当前占位——后续 per-system 计算（经济/战斗/生产）在此扩展
        for (long systemId : assignedSystems) {
            List<Long> entityIds = worldState.entityIdsBySystem.get(systemId);
            if (entityIds != null) {
                // 预留：per-system 经济/战斗/生产计算
            }
        }
    }

    /**
     * 阶段4：合并 Worker 本地缓冲区到全局事件表。
     * 单线程模式直接跳过（无跨线程 pendingOut）。
     */
    public void stage4Merge() {
        // 多线程模式下遍历所有 Worker 缓冲区，合并 pendingOut
        // 当前单线程阶段仅保留钩子
    }

    /**
     * 阶段5：发布快照（钩子，由外部调用 publishRealtimeSnapshotIfNeeded）。
     */
    public void stage5Publish() {
        // 当前由 StarAxisGameRuntime.publishRealtimeSnapshotIfNeeded() 驱动
    }

    // ── 工具 ──

    public Map<Long, Integer> getCurrentAssignment() {
        return currentAssignment;
    }

    public WorkerLocalBuffer getWorkerBuffer(int workerId) {
        if (workerId < 0 || workerId >= workerBuffers.size()) return null;
        return workerBuffers.get(workerId);
    }

    public List<WorkerLocalBuffer> getAllWorkerBuffers() {
        return java.util.Collections.unmodifiableList(workerBuffers);
    }
}
