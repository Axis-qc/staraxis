package staraxis.game.ship;

import staraxis.game.entity.Entity;
import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;
import staraxis.game.world.WorldHexLayout;

public class ShipMovementSystem {
    private static final int SIMPLIFIED_CALCULATION_INTERVAL_TICKS = 4;
    private static final double TARGET_ARRIVAL_THRESHOLD_GU = 0.5;
    private static final double VELOCITY_THRESHOLD = 0.01;

    private final java.util.Map<ShipBody, Integer> simplifiedCalculationCounters = new java.util.HashMap<>();

    public void update(WorldState worldState, double dtGameHours) {
        double dtGameSeconds = dtGameHours * 3600.0;

        for (Entity entity : worldState.entitiesById.values()) {
            if (!(entity instanceof ShipBody ship)) {
                continue;
            }

            if (ship.simplifiedMovementEnabled && ship.movementCommand != null) {
                updateShipMovementSimplified(ship, dtGameSeconds, worldState);
                continue;
            }

            updateHeading(ship, dtGameSeconds);

            if (!ship.isMoving || ship.movementTarget == null) {
                decelerateToStop(ship, dtGameSeconds, worldState);
                continue;
            }

            updateShipMovement(ship, dtGameSeconds, worldState);
        }
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

    private void updateShipMovement(ShipBody ship, double dtGameSeconds, WorldState worldState) {
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

    private void applyVelocity(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        if (ship.velWorldGU == null) {
            return;
        }

        var oldSectorCoord = ship.sectorCoord;
        double newX = ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds;
        double newY = ship.posWorldGU.y() + ship.velWorldGU.y() * dtGameSeconds;
        ship.posWorldGU = new Vec2d(newX, newY);
        ship.sectorCoord = WorldHexLayout.worldToSectorCoord(ship.posWorldGU);

        if (oldSectorCoord != null && !oldSectorCoord.equals(ship.sectorCoord)) {
            if (isDetectorSource(ship, worldState) && ship.ownerNationId != null) {
                worldState.intelSystem.markDirty(ship.ownerNationId);
            }
            worldState.markRealtimeDirty();
        }
    }

    private void completeMoveAtTarget(ShipBody ship, Vec2d targetPosition, WorldState worldState) {
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

    private boolean isDetectorSource(ShipBody ship, WorldState worldState) {
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

    private double normalizeAngle(double angle) {
        while (angle >= 180.0) {
            angle -= 360.0;
        }
        while (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }

    private void updateShipMovementSimplified(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        MovementCommand command = ship.movementCommand;
        if (command == null) {
            updateShipMovement(ship, dtGameSeconds, worldState);
            return;
        }

        int counter = simplifiedCalculationCounters.getOrDefault(ship, 0);
        counter = (counter + 1) % SIMPLIFIED_CALCULATION_INTERVAL_TICKS;
        simplifiedCalculationCounters.put(ship, counter);

        if (counter == 0) {
            updateShipMovement(ship, dtGameSeconds * SIMPLIFIED_CALCULATION_INTERVAL_TICKS, worldState);
        } else {
            updateShipMovementSimplifiedLinear(ship, dtGameSeconds, command, worldState);
        }

        if (!ship.simplifiedMovementEnabled || ship.movementCommand == null) {
            simplifiedCalculationCounters.remove(ship);
        }
    }

    private void updateShipMovementSimplifiedLinear(ShipBody ship, double dtGameSeconds, MovementCommand command,
                                                    WorldState worldState) {
        if (command.commandType == MovementCommand.TYPE_STOP) {
            if (ship.velWorldGU == null) {
                ship.velWorldGU = new Vec2d(0, 0);
                return;
            }

            double currentSpeed = speedOf(ship.velWorldGU);
            if (currentSpeed < VELOCITY_THRESHOLD) {
                ship.velWorldGU = new Vec2d(0, 0);
                ship.isMoving = false;
                ship.lastCompletedClientCommandId = ship.activeClientCommandId;
                ship.activeClientCommandId = null;
                ship.movementCommand = null;
                worldState.markRealtimeDirty();
                return;
            }

            double decelAmount = command.baseAcceleration * dtGameSeconds;
            double newSpeed = Math.max(0, currentSpeed - decelAmount);
            double scale = newSpeed / currentSpeed;

            ship.velWorldGU = new Vec2d(
                ship.velWorldGU.x() * scale,
                ship.velWorldGU.y() * scale
            );
            ship.posWorldGU = new Vec2d(
                ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds,
                ship.posWorldGU.y() + ship.velWorldGU.y() * dtGameSeconds
            );
            return;
        }

        if (command.commandType != MovementCommand.TYPE_MOVE_TO || command.targetPosition == null) {
            return;
        }

        double dx = command.targetPosition.x() - ship.posWorldGU.x();
        double dy = command.targetPosition.y() - ship.posWorldGU.y();
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < TARGET_ARRIVAL_THRESHOLD_GU) {
            completeMoveAtTarget(ship, command.targetPosition, worldState);
            return;
        }

        double dirX = dx / distance;
        double dirY = dy / distance;
        double speed = command.maxSpeed * 0.5;
        ship.velWorldGU = new Vec2d(dirX * speed, dirY * speed);

        double projectedTravelDistance = speed * dtGameSeconds;
        if (projectedTravelDistance >= distance) {
            completeMoveAtTarget(ship, command.targetPosition, worldState);
            return;
        }

        ship.posWorldGU = new Vec2d(
            ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds,
            ship.posWorldGU.y() + ship.velWorldGU.y() * dtGameSeconds
        );

        double targetHeading = Math.toDegrees(Math.atan2(dirY, dirX));
        double headingDiff = normalizeAngle(targetHeading - ship.currentHeadingDeg);
        double maxTurn = command.turnRate * dtGameSeconds;
        if (Math.abs(headingDiff) <= maxTurn) {
            ship.currentHeadingDeg = targetHeading;
        } else {
            ship.currentHeadingDeg += Math.signum(headingDiff) * maxTurn;
        }
        ship.currentHeadingDeg = normalizeAngle(ship.currentHeadingDeg);
        ship.targetHeadingDeg = ship.currentHeadingDeg;
    }

    private double speedOf(Vec2d velocity) {
        return Math.sqrt(velocity.x() * velocity.x() + velocity.y() * velocity.y());
    }
}
