package staraxis.game.ship;

import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;

/**
 * ShipFullMovementSystem（舰船完整移动系统）喵。
 *
 * 作用喵：
 * - 承载玩家视野内舰船的逐 Tick 权威完整计算喵。
 * - 保留完整的转向、加减速、刹车距离和到点收敛逻辑喵。
 */
public class ShipFullMovementSystem extends AbstractShipMovementSystem {

    public void updateShip(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        updateHeading(ship, dtGameSeconds);

        if (!ship.isMoving || ship.movementTarget == null) {
            decelerateToStop(ship, dtGameSeconds, worldState);
            return;
        }

        updateShipMovement(ship, dtGameSeconds, worldState);
    }

    void updateShipMovement(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        double dx = ship.movementTarget.x() - ship.posWorldGU.x();
        double dy = ship.movementTarget.y() - ship.posWorldGU.y();
        double distanceToTarget = Math.sqrt(dx * dx + dy * dy);

        if (distanceToTarget < TARGET_ARRIVAL_THRESHOLD_GU) {
            completeMoveAtTarget(ship, ship.movementTarget, worldState);
            return;
        }

        double moveDirX = dx / distanceToTarget;
        double moveDirY = dy / distanceToTarget;

        double headingRad = Math.toRadians(ship.currentHeadingDeg);
        double bowX = Math.cos(headingRad);
        double bowY = Math.sin(headingRad);
        double dotProduct = moveDirX * bowX + moveDirY * bowY;
        double angleDiff = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct))));

        double effectiveMaxSpeed;
        double effectiveAcceleration;
        if (angleDiff < 45.0) {
            effectiveMaxSpeed = ship.maxSpeed;
            effectiveAcceleration = ship.baseAcceleration + ship.bowAccelerationBonus;
        } else if (angleDiff > 135.0) {
            effectiveMaxSpeed = ship.maxSpeed * ship.reverseSpeedPenalty;
            effectiveAcceleration = ship.baseAcceleration;
        } else {
            effectiveMaxSpeed = ship.maxSpeed * ship.lateralSpeedPenalty;
            effectiveAcceleration = ship.baseAcceleration;
        }

        double currentSpeed = ship.velWorldGU != null ? speedOf(ship.velWorldGU) : 0.0;
        double stopDistance = (currentSpeed * currentSpeed) / (2 * effectiveAcceleration);
        boolean needDecelerate = stopDistance >= distanceToTarget;

        double targetVelX = moveDirX * effectiveMaxSpeed;
        double targetVelY = moveDirY * effectiveMaxSpeed;

        if (needDecelerate) {
            double decelAmount = effectiveAcceleration * dtGameSeconds;
            double newSpeed = Math.max(0, currentSpeed - decelAmount);
            if (currentSpeed > VELOCITY_THRESHOLD) {
                double scale = newSpeed / currentSpeed;
                ship.velWorldGU = new Vec2d(
                    ship.velWorldGU.x() * scale,
                    ship.velWorldGU.y() * scale
                );
            } else {
                ship.velWorldGU = new Vec2d(0, 0);
            }
        } else {
            double currentVelX = ship.velWorldGU != null ? ship.velWorldGU.x() : 0.0;
            double currentVelY = ship.velWorldGU != null ? ship.velWorldGU.y() : 0.0;
            double velDiffX = targetVelX - currentVelX;
            double velDiffY = targetVelY - currentVelY;
            double velDiff = Math.sqrt(velDiffX * velDiffX + velDiffY * velDiffY);

            if (velDiff < VELOCITY_THRESHOLD) {
                ship.velWorldGU = new Vec2d(targetVelX, targetVelY);
            } else {
                double accelAmount = Math.min(velDiff, effectiveAcceleration * dtGameSeconds);
                double ratio = accelAmount / velDiff;
                ship.velWorldGU = new Vec2d(
                    currentVelX + velDiffX * ratio,
                    currentVelY + velDiffY * ratio
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

    private void decelerateToStop(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        if (ship.velWorldGU == null) {
            ship.velWorldGU = new Vec2d(0, 0);
            return;
        }

        double currentSpeed = speedOf(ship.velWorldGU);
        if (currentSpeed < 1.0) {
            ship.velWorldGU = new Vec2d(0, 0);
            return;
        }

        double decelAmount = ship.baseAcceleration * dtGameSeconds;
        double newSpeed = Math.max(0, currentSpeed - decelAmount);
        double scale = newSpeed / currentSpeed;

        ship.velWorldGU = new Vec2d(
            ship.velWorldGU.x() * scale,
            ship.velWorldGU.y() * scale
        );

        applyVelocity(ship, dtGameSeconds, worldState);
    }
}
