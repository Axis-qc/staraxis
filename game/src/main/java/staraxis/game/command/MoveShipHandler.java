package staraxis.game.command;

import staraxis.game.entity.Entity;
import staraxis.game.ship.MovementCommand;
import staraxis.game.ship.ShipBody;
import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;

public class MoveShipHandler implements CommandHandler<MoveShipCommand> {

    @Override
    public void handle(MoveShipCommand command, WorldState worldState, double dtGameHours) {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        if (worldState == null) {
            throw new IllegalArgumentException("world_state_required");
        }

        String nationId = command.getNationId();
        long shipEntityId = command.getShipEntityId();
        double targetX = command.getTargetX();
        double targetY = command.getTargetY();

        Entity entity = worldState.entitiesById.get(shipEntityId);
        if (!(entity instanceof ShipBody ship)) {
            return;
        }
        if (!nationId.equals(ship.ownerNationId)) {
            return;
        }

        ship.movementTarget = new Vec2d(targetX, targetY);
        ship.isMoving = true;
        ship.targetHeadingDeg = Math.toDegrees(Math.atan2(
                targetY - ship.posWorldGU.y(),
                targetX - ship.posWorldGU.x()));

        int simulationTick = worldState.time.simulationTick > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) worldState.time.simulationTick;
        ship.movementCommand = MovementCommand.createMoveTo(
                ship.movementTarget,
                ship,
                worldState.time.getTotalGameSeconds(),
                simulationTick);
    }
}
