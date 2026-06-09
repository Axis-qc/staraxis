package staraxis.game.ship;

import staraxis.game.entity.Entity;
import staraxis.game.state.WorldState;

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

        for (Entity entity : worldState.entitiesById.values()) {
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
