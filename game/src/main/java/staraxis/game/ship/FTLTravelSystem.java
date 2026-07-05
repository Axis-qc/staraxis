package staraxis.game.ship;

import java.util.List;

import staraxis.game.entity.Entity;
import staraxis.game.ship.ShipBody;
import staraxis.game.ship.ShipStatsCalculator;
import staraxis.game.sim.SimulationClock;
import staraxis.game.space.SpacePosition;
import staraxis.game.space.event.CrossSystemEvent;
import staraxis.game.state.WorldState;
import staraxis.game.state.snapshot.EntitySnapshot;

/**
 * FTLTravelSystem（FTL 跳跃系统）。
 *
 * 管理舰船在恒星系之间的 FTL 跳跃（出发 → 在途 → 到达）。
 *
 * 核心逻辑：
 * - 只接受 ShipBody 实体（非舰船实体无法跳跃）。
 * - 读取 ShipBody.galaxySpeedGUps 计算旅行时间（GU/游戏秒 → ticks）。
 * - 读取 ShipBody.fuelPerJump 一次性扣减燃料。
 * - 到达事件由 stage1Arrivals 处理，恢复星系归属。
 */
public class FTLTravelSystem {

    /** 默认 FTL 速度（GU/游戏秒），非舰船实体使用。 */
    public static final double DEFAULT_FTL_SPEED_GU_PER_SECOND = 60_000.0;

    /** FTL 事件ID 起始值（避免与实体ID冲突）。 */
    private long nextEventId = 1_000_000_000L;

    /**
     * 发起一次 FTL 跳跃。
     *
     * 校验顺序：
     * 1. 实体必须是 ShipBody
     * 2. galaxySpeedGUps <= 1 或 fuelPerJump <= 0 → 不能跳跃
     * 3. fuelMass < fuelPerJump → 燃料不足
     * 4. 计算旅行时间，扣减燃料，写入事件表
     *
     * @param worldState     世界状态
     * @param entity         要跳跃的舰船实体
     * @param targetSystemId 目标星系ID
     * @param currentTick    当前游戏 tick
     * @return 创建的事件，或 null（跳跃无效）
     */
    public CrossSystemEvent initiateFTL(WorldState worldState, Entity entity, long targetSystemId, long currentTick) {
        if (entity == null || entity.systemId <= 0 || targetSystemId <= 0) {
            return null;
        }
        if (entity.systemId == targetSystemId) {
            return null;
        }
        if (worldState.crossSystemEventTable.isInTransit(entity.entityId)) {
            return null;
        }

        // 校验 1：只接受 ShipBody
        if (!(entity instanceof ShipBody ship)) {
            return null;
        }

        // 从 calculator 获取舰船移动属性
        var stats = ShipStatsCalculator.computeMovementStats(ship, null, null);

        // 校验 2：引擎能力检查
        if (stats.galaxySpeedGUps() <= 1.0 || stats.fuelPerJump() <= 0) {
            return null; // 无曲速引擎或引擎不够快
        }

        // 校验 3：燃料检查
        if (ship.fuelMass < stats.fuelPerJump()) {
            return null; // 燃料不足
        }

        long sourceSystemId = entity.systemId;

        // 扣减燃料（fuelPerJump 从 stats 获取）
        ship.fuelMass -= stats.fuelPerJump();

        // 计算旅行时间：galaxySpeedGUps 是 GU/游戏秒，先转秒再转 tick
        double travelTimeSeconds = computeTravelTimeSeconds(worldState, sourceSystemId, targetSystemId, stats.galaxySpeedGUps());
        long travelTimeTicks = Math.max((long) Math.ceil(travelTimeSeconds * SimulationClock.TICKS_PER_SECOND), 10);

        // 从源星系索引中移除实体
        List<Long> sourceEntities = worldState.entityIdsBySystem.get(sourceSystemId);
        if (sourceEntities != null) {
            sourceEntities.remove(entity.entityId);
        }

        // 标记实体为在途
        entity.systemId = 0;

        // 创建实体快照
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
     * 处理到达事件的逻辑已统一归入 TickDispatcher.stage1Arrivals()。
     * 此处保留方法签名供未来在途结算扩展使用，当前不调用。
     */
    @SuppressWarnings("unused")
    public void processArrivingEvents(WorldState worldState, long currentTick) {
        // 到达逻辑由 TickDispatcher.stage1Arrivals() 在 tick 中处理
        // 该方法预留用于：到达时恢复在途期间消耗（燃料、事件等）
    }

    /**
     * 计算两个星系之间的 FTL 旅行时间（游戏秒）。
     *
     * @param worldState  世界状态
     * @param fromSystemId 源星系ID
     * @param toSystemId   目标星系ID
     * @param speedGUps   舰船 FTL 速度（GU/游戏秒）
     * @return 旅行时间（游戏秒）
     */
    private double computeTravelTimeSeconds(WorldState worldState, long fromSystemId, long toSystemId, double speedGUps) {
        SpacePosition fromPos = worldState.systemPositions.get(fromSystemId);
        SpacePosition toPos = worldState.systemPositions.get(toSystemId);
        if (fromPos == null || toPos == null) {
            // 星系位置缺失，使用默认速度估算
            double defaultDistance = 2_000_000.0;
            return defaultDistance / (speedGUps > 0 ? speedGUps : DEFAULT_FTL_SPEED_GU_PER_SECOND);
        }
        double distance = fromPos.distanceTo(toPos);
        return distance / speedGUps;
    }

    @SuppressWarnings("unused")
    private EntitySnapshot createSnapshot(Entity entity) {
        return null;
    }
}
