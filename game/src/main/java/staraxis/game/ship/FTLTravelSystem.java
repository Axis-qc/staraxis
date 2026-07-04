package staraxis.game.ship;

import staraxis.game.entity.Entity;
import staraxis.game.state.WorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.space.SpacePosition;
import staraxis.game.space.event.CrossSystemEvent;
import staraxis.game.space.event.CrossSystemEventTable;

import java.util.List;

/**
 * FTLTravelSystem（FTL 跳跃系统）。
 *
 * 管理舰船在恒星系之间的 FTL 跳跃（出发 → 在途 → 到达）。
 *
 * 当前为单线程版本（阶段2），核心功能：
 * - initiateFTL()：出发时从源星系移除实体，写入跨系统事件表
 * - processArrivingEvents()：每 tick 处理到达事件，将实体恢复到目标星系
 *
 * 多线程版本（阶段4）将引入 Worker 本地缓冲区分发合并。
 */
public class FTLTravelSystem {

    /** 默认 FTL 速度（GU/tick），约 3333 GU/tick = 2,000,000 GU/600 tick ≈ 10s 穿越星系间距。 */
    public static final double DEFAULT_FTL_SPEED_GU_PER_TICK = 3_333.0;

    /** FTL 事件ID 起始值（避免与实体ID冲突）。 */
    private long nextEventId = 1_000_000_000L;

    /**
     * 发起一次 FTL 跳跃。
     * 将实体从源星系的 entityIdsBySystem 中移除，写入跨系统事件表标记为在途。
     *
     * @param worldState 世界状态
     * @param entity     要跳跃的实体
     * @param targetSystemId 目标星系ID（须存在于 systemPositions 中）
     * @param currentTick   当前游戏 tick
     * @return 创建的事件，或 null（跳跃无效时）
     */
    public CrossSystemEvent initiateFTL(WorldState worldState, Entity entity, long targetSystemId, long currentTick) {
        if (entity == null || entity.systemId <= 0 || targetSystemId <= 0) {
            return null;
        }
        if (entity.systemId == targetSystemId) {
            return null; // 已在目标星系
        }
        if (worldState.crossSystemEventTable.isInTransit(entity.entityId)) {
            return null; // 已在途中
        }

        long sourceSystemId = entity.systemId;

        // 计算旅行时间
        long travelTimeTicks = computeTravelTime(worldState, sourceSystemId, targetSystemId);
        if (travelTimeTicks <= 0) {
            travelTimeTicks = 1; // 最小 1 tick
        }

        // 从源星系索引中移除实体
        List<Long> sourceEntities = worldState.entityIdsBySystem.get(sourceSystemId);
        if (sourceEntities != null) {
            sourceEntities.remove(entity.entityId);
        }

        // 标记实体为在途（systemId=0 表示不归属任何星系）
        entity.systemId = 0;

        // 创建实体快照（出发时状态快照，到达时用于结算）
        EntitySnapshot snapshot = createSnapshot(entity);

        // 创建事件并写入事件表
        long eventId = nextEventId++;
        CrossSystemEvent event = new CrossSystemEvent(
                eventId, entity.entityId, sourceSystemId, targetSystemId,
                CrossSystemEvent.EventType.FTL_DEPARTURE,
                currentTick, currentTick + travelTimeTicks,
                snapshot);
        worldState.crossSystemEventTable.addEvent(event);

        return event;
    }

    /**
     * 处理当前 tick 到期的到达事件（Tick 流水线阶段1）。
     * 将实体恢复到目标星系的 entityIdsBySystem 中。
     *
     * @param worldState 世界状态
     * @param currentTick 当前游戏 tick
     */
    public void processArrivingEvents(WorldState worldState, long currentTick) {
        CrossSystemEventTable table = worldState.crossSystemEventTable;
        List<CrossSystemEvent> dueEvents = table.getEventsDueAt(currentTick);

        for (CrossSystemEvent event : dueEvents) {
            Entity entity = worldState.entitiesById.get(event.entityId);
            if (entity == null) continue;

            // 恢复实体到目标星系
            entity.systemId = event.targetSystemId;
            worldState.entityIdsBySystem
                    .computeIfAbsent(event.targetSystemId, k -> new java.util.ArrayList<>())
                    .add(entity.entityId);

            // 如果快照中有位置信息，恢复位置
            if (event.snapshot != null) {
                // 在途期间可能消耗了燃料等——将在完整版本中处理
                // 当前单线程阶段仅恢复星系归属
            }
        }
    }

    /**
     * 计算两个星系之间的 FTL 旅行时间（tick 数）。
     */
    private long computeTravelTime(WorldState worldState, long fromSystemId, long toSystemId) {
        SpacePosition fromPos = worldState.systemPositions.get(fromSystemId);
        SpacePosition toPos = worldState.systemPositions.get(toSystemId);
        if (fromPos == null || toPos == null) {
            return 600; // 默认 600 tick ≈ 10s
        }
        double distance = fromPos.distanceTo(toPos);
        long ticks = (long) Math.ceil(distance / DEFAULT_FTL_SPEED_GU_PER_TICK);
        return Math.max(ticks, 10); // 至少 10 tick，避免秒到
    }

    /**
     * 创建实体快照（当前为占位，后续扩展）。
     */
    @SuppressWarnings("unused")
    private EntitySnapshot createSnapshot(Entity entity) {
        // 单线程阶段不需要完整的深拷贝快照
        // 传 null 表示"到达时保持 entitiesById 中的状态"
        return null;
    }
}
