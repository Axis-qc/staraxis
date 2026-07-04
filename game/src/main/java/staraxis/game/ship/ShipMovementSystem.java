package staraxis.game.ship;

import staraxis.game.entity.Entity;
import staraxis.game.state.WorldState;

import java.util.List;

/**
 * ShipMovementSystem（舰船移动分发系统）喵。
 *
 * 作用喵：
 * - 遍历全世界舰船实体喵。
 * - 根据“是否必须走完整实时计算”把舰船路由到完整系统或简化系统喵。
 */
public class ShipMovementSystem {
    private final ShipFullMovementSystem fullMovementSystem = new ShipFullMovementSystem();
    private final ShipSimplifiedMovementSystem simplifiedMovementSystem =
        new ShipSimplifiedMovementSystem(fullMovementSystem);

    public void update(WorldState worldState, double dtGameHours) {
        double dtGameSeconds = dtGameHours * 3600.0;

        // 按恒星系遍历：性能优化 + 去掉旧 hex 转换依赖
        // 在途实体（systemId=0 / DEEP_SPACE）由跨System事件表（阶段2）处理
        for (List<Long> entityIds : worldState.entityIdsBySystem.values()) {
            for (long entityId : entityIds) {
                Entity entity = worldState.entitiesById.get(entityId);
                if (!(entity instanceof ShipBody ship)) {
                    continue;
                }

                if (
                    ship.simplifiedMovementEnabled
                    && ship.movementCommand != null
                    && !worldState.shouldUseFullRealtimeSimulation(ship.entityId)
                ) {
                    simplifiedMovementSystem.updateShip(ship, dtGameSeconds, worldState);
                    continue;
                }

                fullMovementSystem.updateShip(ship, dtGameSeconds, worldState);
            }
        }
    }
}
