package staraxis.game.command;

import staraxis.game.entity.Entity;
import staraxis.game.space.SpacePosition;
import staraxis.game.ship.MovementCommand;
import staraxis.game.ship.ShipBody;
import staraxis.game.state.WorldState;

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
        String clientCommandId = command.getClientCommandId();
        long shipEntityId = command.getShipEntityId();
        double targetX = command.getTargetX();
        double targetY = command.getTargetY();
        double targetZ = command.getTargetZ();

        Entity entity = worldState.entitiesById.get(shipEntityId);
        if (!(entity instanceof ShipBody ship)) {
            return;
        }
        // 鉴权:舰船有 owner 时校验指令发起者是否匹配;无主舰船(ownerNationId==null)直接执行喵
        // 设计原则:鉴权与指令执行分离 - 无主舰船任何指令都可执行,有主舰船才做匹配校验
        if (ship.ownerNationId != null && !ship.ownerNationId.equals(nationId)) {
            return;
        }

        ship.movementTarget = new SpacePosition(targetX, targetY, targetZ);
        ship.isMoving = true;
        ship.targetHeadingDeg = Math.toDegrees(Math.atan2(
                targetZ - ship.posWorldGU.z(),
                targetX - ship.posWorldGU.x()));
        ship.activeClientCommandId = clientCommandId;

        int simulationTick = worldState.time.simulationTick > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) worldState.time.simulationTick;
        ship.movementCommand = MovementCommand.createMoveTo(
                ship.movementTarget,
                ship,
                clientCommandId,
                worldState.time.totalGameSecondsAcc,
                simulationTick);
        worldState.markRealtimeDirty();
    }
}
