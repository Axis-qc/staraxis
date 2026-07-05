/*
 * ShipFullMovementSystem
 *
 * 文件作用：
 * - 自由移动（直飞）子系统，纯直飞：加速→巡航→减速→到达。
 *
 * 使用方式：
 * - 由 ShipMovementSystem 在 isMoving=true 且 movementTarget!=null 时调用。
 *
 * 注意事项：
 * - 不处理轨道物理，不处理朝向/转向。
 * - Y 轴在此系统中被忽略（保持现有行为），后续 3D 化时统一处理。
 */

package staraxis.game.ship;

import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

/**
 * ShipFullMovementSystem（舰船自由移动系统）。
 *
 * 纯直飞模式：直线加速到目标，到达收敛。
 */
public class ShipFullMovementSystem extends AbstractShipMovementSystem {

    /**
     * 更新舰船自由移动。
     *
     * @param ship          舰船
     * @param dtGameSeconds 时间步长（游戏秒）
     * @param worldState    世界状态
     */
    public void updateFreeMove(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        if (!ship.isMoving || ship.movementTarget == null) {
            decelerateToStop(ship, dtGameSeconds, worldState);
            return;
        }

        double dx = ship.movementTarget.x() - ship.posWorldGU.x();
        double dy = ship.movementTarget.y() - ship.posWorldGU.y();
        double dz = ship.movementTarget.z() - ship.posWorldGU.z();
        double distanceToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distanceToTarget < TARGET_ARRIVAL_THRESHOLD_GU) {
            completeMoveAtTarget(ship, ship.movementTarget, worldState);
            return;
        }

        double dirX = dx / distanceToTarget;
        double dirY = dy / distanceToTarget;
        double dirZ = dz / distanceToTarget;

        // 从 calculator 获取速度/加速度
        var stats = ShipStatsCalculator.computeMovementStats(ship, null, null);
        double targetSpeed = stats.maxSpeed();
        double accel = stats.baseAcceleration();

        double currentSpeed = ship.velWorldGU != null ? speedOf(ship.velWorldGU) : 0.0;
        double stopDistance = (currentSpeed * currentSpeed) / (2 * accel);
        boolean needDecelerate = stopDistance >= distanceToTarget;

        double targetVelX = dirX * targetSpeed;
        double targetVelY = dirY * targetSpeed;
        double targetVelZ = dirZ * targetSpeed;

        if (needDecelerate) {
            double decelAmount = accel * dtGameSeconds;
            double newSpeed = Math.max(0, currentSpeed - decelAmount);
            if (currentSpeed > VELOCITY_THRESHOLD) {
                double scale = newSpeed / currentSpeed;
                ship.velWorldGU = new SpacePosition(
                    ship.velWorldGU.x() * scale,
                    ship.velWorldGU.y() * scale,
                    ship.velWorldGU.z() * scale
                );
            } else {
                ship.velWorldGU = SpacePosition.ORIGIN;
            }
        } else {
            double currentVelX = ship.velWorldGU != null ? ship.velWorldGU.x() : 0.0;
            double currentVelY = ship.velWorldGU != null ? ship.velWorldGU.y() : 0.0;
            double currentVelZ = ship.velWorldGU != null ? ship.velWorldGU.z() : 0.0;
            double velDiffX = targetVelX - currentVelX;
            double velDiffY = targetVelY - currentVelY;
            double velDiffZ = targetVelZ - currentVelZ;
            double velDiff = Math.sqrt(velDiffX * velDiffX + velDiffY * velDiffY + velDiffZ * velDiffZ);

            if (velDiff < VELOCITY_THRESHOLD) {
                ship.velWorldGU = new SpacePosition(targetVelX, targetVelY, targetVelZ);
            } else {
                double accelAmount = Math.min(velDiff, accel * dtGameSeconds);
                double ratio = accelAmount / velDiff;
                ship.velWorldGU = new SpacePosition(
                    currentVelX + velDiffX * ratio,
                    currentVelY + velDiffY * ratio,
                    currentVelZ + velDiffZ * ratio
                );
            }
        }

        double projectedTravelDistance = ship.velWorldGU != null ? speedOf(ship.velWorldGU) * dtGameSeconds : 0.0;
        if (projectedTravelDistance >= distanceToTarget) {
            completeMoveAtTarget(ship, ship.movementTarget, worldState);
            return;
        }

        applyVelocity(ship, dtGameSeconds, worldState);
    }

    private void decelerateToStop(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        if (ship.velWorldGU == null || speedOf(ship.velWorldGU) < VELOCITY_THRESHOLD) {
            ship.velWorldGU = SpacePosition.ORIGIN;
            return;
        }

        var stats = ShipStatsCalculator.computeMovementStats(ship, null, null);
        double accel = stats.baseAcceleration();
        double currentSpeed = speedOf(ship.velWorldGU);
        double decelAmount = accel * dtGameSeconds;
        double newSpeed = Math.max(0, currentSpeed - decelAmount);
        double scale = newSpeed / currentSpeed;

        ship.velWorldGU = new SpacePosition(
            ship.velWorldGU.x() * scale,
            ship.velWorldGU.y() * scale,
            ship.velWorldGU.z() * scale
        );

        if (newSpeed <= VELOCITY_THRESHOLD) {
            ship.velWorldGU = SpacePosition.ORIGIN;
        }

        applyVelocity(ship, dtGameSeconds, worldState);
    }
}
