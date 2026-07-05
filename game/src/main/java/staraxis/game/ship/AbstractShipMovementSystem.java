/*
 * AbstractShipMovementSystem
 *
 * 文件作用：
 * - 舰船移动系统共享工具基类。
 * - 提供 applyVelocity()、completeMoveAtTarget() 等直飞模式辅助方法。
 *
 * 使用方式：
 * - ShipFullMovementSystem 继承此类使用共享方法。
 * - 常量 TARGET_ARRIVAL_THRESHOLD_GU / VELOCITY_THRESHOLD 供各子系统引用。
 */

package staraxis.game.ship;

import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

/**
 * AbstractShipMovementSystem（舰船移动共享工具基类）。
 */
abstract class AbstractShipMovementSystem {

    /** 到达目标的判定阈值（GU）。 */
    protected static final double TARGET_ARRIVAL_THRESHOLD_GU = 0.5;

    /** 速度归零阈值（GU/秒）。 */
    protected static final double VELOCITY_THRESHOLD = 0.01;

    protected double speedOf(SpacePosition velocity) {
        return velocity.length();
    }

    protected void applyVelocity(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        if (ship.velWorldGU == null) {
            return;
        }

        SpacePosition previousPosition = ship.posWorldGU;
        double newX = ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds;
        double newY = ship.posWorldGU.y() + ship.velWorldGU.y() * dtGameSeconds;
        double newZ = ship.posWorldGU.z() + ship.velWorldGU.z() * dtGameSeconds;
        ship.posWorldGU = new SpacePosition(newX, newY, newZ);

        if (previousPosition == null
            || Math.abs(previousPosition.x() - ship.posWorldGU.x()) > 1e-9
            || Math.abs(previousPosition.y() - ship.posWorldGU.y()) > 1e-9
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
}
