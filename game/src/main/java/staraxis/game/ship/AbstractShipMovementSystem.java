package staraxis.game.ship;

import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;
import staraxis.game.world.WorldHexLayout;

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

    protected double speedOf(Vec2d velocity) {
        return Math.sqrt(velocity.x() * velocity.x() + velocity.y() * velocity.y());
    }

    protected void applyVelocity(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        if (ship.velWorldGU == null) {
            return;
        }

        var oldSectorCoord = ship.sectorCoord;
        Vec2d previousPosition = ship.posWorldGU;
        double newX = ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds;
        double newY = ship.posWorldGU.y() + ship.velWorldGU.y() * dtGameSeconds;
        ship.posWorldGU = new Vec2d(newX, newY);
        ship.sectorCoord = WorldHexLayout.worldToSectorCoord(ship.posWorldGU);

        // 舰船实时移动时，每个逻辑 tick 的位置变化都必须进入高频快照喵，
        // 不能只在跨星区时才标记实时状态为脏喵。
        if (previousPosition == null
            || Math.abs(previousPosition.x() - ship.posWorldGU.x()) > 1e-9
            || Math.abs(previousPosition.y() - ship.posWorldGU.y()) > 1e-9) {
            worldState.markRealtimeDirty();
        }

        if (oldSectorCoord != null && !oldSectorCoord.equals(ship.sectorCoord)) {
            if (isDetectorSource(ship, worldState) && ship.ownerNationId != null) {
                worldState.intelSystem.markDirty(ship.ownerNationId);
            }
        }
    }

    protected void completeMoveAtTarget(ShipBody ship, Vec2d targetPosition, WorldState worldState) {
        if (targetPosition != null) {
            ship.posWorldGU = targetPosition;
        }
        ship.movementTarget = null;
        ship.isMoving = false;
        ship.velWorldGU = new Vec2d(0, 0);
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
