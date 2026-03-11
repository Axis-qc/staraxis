package staraxis.game.ship;

import staraxis.game.entity.Entity;
import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;
import staraxis.game.world.WorldHexLayout;

/**
 * ShipMovementSystem（舰船移动系统）喵。
 *
 * 职责：
 * - 处理所有舰船的物理移动计算喵。
 * - 支持全向移动：无需转向即可向任意方向移动喵。
 * - 舰首朝向加成：移动方向与舰首一致时有额外加速度喵。
 * - 侧向/反向惩罚：垂直或反向移动时有速度惩罚喵。
 * - 权威位置更新只在后端执行，前端基于快照同步喵。
 */
public class ShipMovementSystem {

    /**
     * 更新所有舰船的移动状态喵。
     *
     * @param worldState  世界状态
     * @param dtGameHours 本次 tick 的游戏小时数
     */
    public void update(WorldState worldState, double dtGameHours) {
        double dtGameSeconds = dtGameHours * 3600.0;

        for (Entity entity : worldState.entitiesById.values()) {
            if (!(entity instanceof ShipBody ship)) {
                continue;
            }

            // 更新舰首朝向（始终转向目标朝向）喵
            updateHeading(ship, dtGameSeconds);

            if (!ship.isMoving || ship.movementTarget == null) {
                // 不在移动状态时减速到停止喵
                decelerateToStop(ship, dtGameSeconds);
                continue;
            }

            updateShipMovement(ship, dtGameSeconds);
        }
    }

    /**
     * 更新舰首朝向喵。
     */
    private void updateHeading(ShipBody ship, double dtGameSeconds) {
        double headingDiff = normalizeAngle(ship.targetHeadingDeg - ship.currentHeadingDeg);
        double maxTurn = ship.turnRate * dtGameSeconds;

        if (Math.abs(headingDiff) <= maxTurn) {
            ship.currentHeadingDeg = ship.targetHeadingDeg;
        } else {
            ship.currentHeadingDeg += Math.signum(headingDiff) * maxTurn;
        }
        ship.currentHeadingDeg = normalizeAngle(ship.currentHeadingDeg);
    }

    /**
     * 减速到停止喵。
     */
    private void decelerateToStop(ShipBody ship, double dtGameSeconds) {
        if (ship.velWorldGU == null) {
            ship.velWorldGU = new Vec2d(0, 0);
            return;
        }

        double currentSpeed = Math.sqrt(
            ship.velWorldGU.x() * ship.velWorldGU.x() +
            ship.velWorldGU.y() * ship.velWorldGU.y()
        );

        if (currentSpeed < 1.0) {
            ship.velWorldGU = new Vec2d(0, 0);
            return;
        }

        // 使用基础加速度减速喵
        double decelAmount = ship.baseAcceleration * dtGameSeconds;
        double newSpeed = Math.max(0, currentSpeed - decelAmount);
        double scale = newSpeed / currentSpeed;

        ship.velWorldGU = new Vec2d(
            ship.velWorldGU.x() * scale,
            ship.velWorldGU.y() * scale
        );

        applyVelocity(ship, dtGameSeconds);
    }

    /**
     * 更新单个舰船的移动（全向移动）喵。
     */
    private void updateShipMovement(ShipBody ship, double dtGameSeconds) {
        // 计算到目标的距离和期望移动方向喵
        double dx = ship.movementTarget.x() - ship.posWorldGU.x();
        double dy = ship.movementTarget.y() - ship.posWorldGU.y();
        double distanceToTarget = Math.sqrt(dx * dx + dy * dy);

        // 如果已到达目标（阈值 20 GU），停止移动喵
        if (distanceToTarget < 20.0) {
            ship.isMoving = false;
            ship.velWorldGU = new Vec2d(0, 0);
            return;
        }

        // 期望移动方向（单位向量）喵
        double moveDirX = dx / distanceToTarget;
        double moveDirY = dy / distanceToTarget;

        // 计算当前舰首朝向的单位向量喵
        double headingRad = Math.toRadians(ship.currentHeadingDeg);
        double bowX = Math.cos(headingRad);
        double bowY = Math.sin(headingRad);

        // 计算移动方向与舰首朝向的夹角喵
        double dotProduct = moveDirX * bowX + moveDirY * bowY;
        double angleDiff = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct))));

        // 根据夹角确定移动类型和参数喵
        double effectiveMaxSpeed;
        double effectiveAcceleration;

        if (angleDiff < 45.0) {
            // 正向移动（与舰首方向夹角小于45度）喵
            // 舰首朝向加成：全额加速度和最高速度喵
            effectiveMaxSpeed = ship.maxSpeed;
            effectiveAcceleration = ship.baseAcceleration + ship.bowAccelerationBonus;
        } else if (angleDiff > 135.0) {
            // 反向移动（与舰首方向夹角大于135度）喵
            effectiveMaxSpeed = ship.maxSpeed * ship.reverseSpeedPenalty;
            effectiveAcceleration = ship.baseAcceleration;
        } else {
            // 侧向移动（夹角在45-135度之间）喵
            effectiveMaxSpeed = ship.maxSpeed * ship.lateralSpeedPenalty;
            effectiveAcceleration = ship.baseAcceleration;
        }

        // 检查是否需要减速（距离目标是否足够近）喵
        double currentSpeed = ship.velWorldGU != null
            ? Math.sqrt(ship.velWorldGU.x() * ship.velWorldGU.x()
                      + ship.velWorldGU.y() * ship.velWorldGU.y())
            : 0;

        double stopDistance = (currentSpeed * currentSpeed) / (2 * effectiveAcceleration);
        boolean needDecelerate = stopDistance >= distanceToTarget;

        // 计算目标速度矢量喵
        double targetVelX = moveDirX * effectiveMaxSpeed;
        double targetVelY = moveDirY * effectiveMaxSpeed;

        // 更新速度喵
        if (needDecelerate) {
            // 减速到目标喵
            double decelAmount = effectiveAcceleration * dtGameSeconds;
            double newSpeed = Math.max(0, currentSpeed - decelAmount);
            if (currentSpeed > 0.01) {
                double scale = newSpeed / currentSpeed;
                ship.velWorldGU = new Vec2d(
                    ship.velWorldGU.x() * scale,
                    ship.velWorldGU.y() * scale
                );
            } else {
                ship.velWorldGU = new Vec2d(0, 0);
            }
        } else {
            // 向目标速度加速喵
            double currentVelX = ship.velWorldGU != null ? ship.velWorldGU.x() : 0;
            double currentVelY = ship.velWorldGU != null ? ship.velWorldGU.y() : 0;

            double velDiffX = targetVelX - currentVelX;
            double velDiffY = targetVelY - currentVelY;
            double velDiff = Math.sqrt(velDiffX * velDiffX + velDiffY * velDiffY);

            if (velDiff < 0.01) {
                // 已接近目标速度喵
                ship.velWorldGU = new Vec2d(targetVelX, targetVelY);
            } else {
                // 向目标速度加速喵
                double accelAmount = Math.min(velDiff, effectiveAcceleration * dtGameSeconds);
                double ratio = accelAmount / velDiff;
                ship.velWorldGU = new Vec2d(
                    currentVelX + velDiffX * ratio,
                    currentVelY + velDiffY * ratio
                );
            }
        }

        // 应用速度更新位置喵
        applyVelocity(ship, dtGameSeconds);
    }

    /**
     * 应用速度更新位置喵。
     */
    private void applyVelocity(ShipBody ship, double dtGameSeconds) {
        if (ship.velWorldGU == null) {
            return;
        }

        double newX = ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds;
        double newY = ship.posWorldGU.y() + ship.velWorldGU.y() * dtGameSeconds;
        ship.posWorldGU = new Vec2d(newX, newY);

        // 更新星区坐标喵
        ship.sectorCoord = WorldHexLayout.worldToSectorCoord(ship.posWorldGU);
    }

    /**
     * 将角度标准化到 [-180, 180) 范围喵。
     */
    private double normalizeAngle(double angle) {
        while (angle >= 180.0) {
            angle -= 360.0;
        }
        while (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }
}
