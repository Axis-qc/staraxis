package staraxis.game.ship;

import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

/**
 * AbstractShipMovementSystem（舰船移动共享工具基类）喵。
 *
 * 作用喵：
 * - 收敛完整移动系统与简化移动系统共用的物理常量与辅助方法喵。
 * - 避免完整计算和简化计算各自复制一套到处散落的物理逻辑喵。
 */
abstract class AbstractShipMovementSystem {

    protected static final double TARGET_ARRIVAL_THRESHOLD_GU = 0.5;
    protected static final double VELOCITY_THRESHOLD = 0.01;

    protected double normalizeAngle(double angle) {
        while (angle >= 180.0) {
            angle -= 360.0;
        }
        while (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }

    protected double speedOf(SpacePosition velocity) {
        return velocity.length();
    }

    protected void applyVelocity(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        if (ship.velWorldGU == null) {
            return;
        }

        SpacePosition previousPosition = ship.posWorldGU;
        double newX = ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds;
        double newZ = ship.posWorldGU.z() + ship.velWorldGU.z() * dtGameSeconds;
        ship.posWorldGU = new SpacePosition(newX, 0, newZ);

        // 舰船实时移动时，每个逻辑 tick 的位置变化都必须进入高频快照喵，
        // 不能只在跨星区时才标记实时状态为脏喵。
        if (previousPosition == null
            || Math.abs(previousPosition.x() - ship.posWorldGU.x()) > 1e-9
            || Math.abs(previousPosition.z() - ship.posWorldGU.z()) > 1e-9) {
            worldState.markRealtimeDirty();
        }
    }

    protected void completeMoveAtTarget(ShipBody ship, SpacePosition targetPosition, WorldState worldState) {
        if (targetPosition != null) {
            ship.posWorldGU = targetPosition;
        }
        ship.movementTarget = null;
        ship.isMoving = false;
        ship.velWorldGU = SpacePosition.ORIGIN;
        ship.lastCompletedClientCommandId = ship.activeClientCommandId;
        ship.activeClientCommandId = null;
        ship.movementCommand = null;
        worldState.markRealtimeDirty();
    }

    protected boolean isDetectorSource(ShipBody ship, WorldState worldState) {
        if (worldState.intelSystem == null) {
            return false;
        }
        var config = worldState.intelSystem.getConfig();
        if (config == null) {
            return false;
        }
        return config.detectorSourceStrengthByEntityType.containsKey(ship.entityType)
            && config.detectorSourceRangeByEntityType.containsKey(ship.entityType);
    }
}
