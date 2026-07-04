package staraxis.game.space.event;

import staraxis.game.state.snapshot.EntitySnapshot;

/**
 * CrossSystemEvent（跨系统事件）。
 *
 * 记录实体从一个恒星系到另一个恒星系的移动事件。
 * 由 FTLTravelSystem 创建，由 TickDispatcher 的阶段1处理到达。
 *
 * 生命周期：FTL_DEPARTURE → 事件表（在途）→ FTL_ARRIVAL（阶段1处理）
 */
public class CrossSystemEvent {

    public enum EventType {
        FTL_DEPARTURE,   // 舰船开始 FTL 跳跃
        FTL_ARRIVAL,     // 舰船到达目标星系
        FTL_INTERRUPT,   // FTL 被中断（战斗/引力井干扰）
        SENSOR_REPORT,   // 远程传感器探测
        REINFORCEMENT,   // 增援舰队到达
    }

    /** 事件唯一ID。 */
    public final long eventId;

    /** 移动中的实体ID。 */
    public final long entityId;

    /** 出发星系ID。 */
    public final long sourceSystemId;

    /** 目标星系ID。 */
    public final long targetSystemId;

    /** 事件类型。 */
    public final EventType type;

    /** 出发时的游戏 tick。 */
    public final long departureTick;

    /** 到达时的游戏 tick。 */
    public final long arrivalTick;

    /** 出发时的实体快照（用于到达时恢复状态）。 */
    public final EntitySnapshot snapshot;

    public CrossSystemEvent(long eventId, long entityId, long sourceSystemId, long targetSystemId,
                           EventType type, long departureTick, long arrivalTick, EntitySnapshot snapshot) {
        this.eventId = eventId;
        this.entityId = entityId;
        this.sourceSystemId = sourceSystemId;
        this.targetSystemId = targetSystemId;
        this.type = type;
        this.departureTick = departureTick;
        this.arrivalTick = arrivalTick;
        this.snapshot = snapshot;
    }
}
