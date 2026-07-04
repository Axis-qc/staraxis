package staraxis.game.ship;

import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

/**
 * ShipSimplifiedMovementSystem（舰船简化移动系统）喵。
 *
 * 作用喵：
 * - 仅供世界级并集关注集合之外的实体使用喵。
 * - 在不需要玩家眼前逐 Tick 平滑表现的场景下喵，提供较轻量的近似推进喵。
 */
public class ShipSimplifiedMovementSystem extends AbstractShipMovementSystem {

    // 当前阶段优先保证权威实时快照的逐 Tick 连续性喵。
    // 若这里大于 1 喵，简化移动模式就会隔多个 Tick 才做一次完整推进喵，
    // 前端即使每 Tick 都收到快照喵，也会看到明显的分段速度跳变喵。
    // 先收敛到每 Tick 都完整计算一次喵，后续若要重上简化模式，再重新设计口径喵。
    private static final int SIMPLIFIED_CALCULATION_INTERVAL_TICKS = 1;

    private final java.util.Map<ShipBody, Integer> simplifiedCalculationCounters = new java.util.HashMap<>();
    private final ShipFullMovementSystem fullMovementSystem;

    public ShipSimplifiedMovementSystem(ShipFullMovementSystem fullMovementSystem) {
        this.fullMovementSystem = fullMovementSystem;
    }

    public void updateShip(ShipBody ship, double dtGameSeconds, WorldState worldState) {
        MovementCommand command = ship.movementCommand;
        if (command == null) {
            fullMovementSystem.updateShip(ship, dtGameSeconds, worldState);
            return;
        }

        int counter = simplifiedCalculationCounters.getOrDefault(ship, 0);
        counter = (counter + 1) % SIMPLIFIED_CALCULATION_INTERVAL_TICKS;
        simplifiedCalculationCounters.put(ship, counter);

        if (counter == 0) {
            fullMovementSystem.updateShipMovement(ship, dtGameSeconds * SIMPLIFIED_CALCULATION_INTERVAL_TICKS, worldState);
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
                ship.velWorldGU = SpacePosition.ORIGIN;
                return;
            }

            double currentSpeed = speedOf(ship.velWorldGU);
            if (currentSpeed < VELOCITY_THRESHOLD) {
                ship.velWorldGU = SpacePosition.ORIGIN;
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

            ship.velWorldGU = new SpacePosition(
                ship.velWorldGU.x() * scale,
                0,
                ship.velWorldGU.z() * scale
            );
            ship.posWorldGU = new SpacePosition(
                ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds,
                0,
                ship.posWorldGU.z() + ship.velWorldGU.z() * dtGameSeconds
            );
            return;
        }

        if (command.commandType != MovementCommand.TYPE_MOVE_TO || command.targetPosition == null) {
            return;
        }

        double dx = command.targetPosition.x() - ship.posWorldGU.x();
        double dz = command.targetPosition.z() - ship.posWorldGU.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < TARGET_ARRIVAL_THRESHOLD_GU) {
            completeMoveAtTarget(ship, command.targetPosition, worldState);
            return;
        }

        double dirX = dx / distance;
        double dirZ = dz / distance;
        double speed = command.maxSpeed * 0.5;
        ship.velWorldGU = new SpacePosition(dirX * speed, 0, dirZ * speed);

        double projectedTravelDistance = speed * dtGameSeconds;
        if (projectedTravelDistance >= distance) {
            completeMoveAtTarget(ship, command.targetPosition, worldState);
            return;
        }

        ship.posWorldGU = new SpacePosition(
            ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds,
            0,
            ship.posWorldGU.z() + ship.velWorldGU.z() * dtGameSeconds
        );
        worldState.markRealtimeDirty();

        double targetHeading = Math.toDegrees(Math.atan2(dirZ, dirX));
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
}
