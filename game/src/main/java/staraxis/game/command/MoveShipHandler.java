package staraxis.game.command;

import staraxis.game.entity.Entity;
import staraxis.game.ship.ShipBody;
import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;

/**
 * MoveShipHandler（移动舰船处理器）喵。
 *
 * 处理 MoveShipCommand，设置舰船的目标移动位置喵。
 */
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

        // 获取舰船实体喵
        Entity entity = worldState.entitiesById.get(shipEntityId);
        if (entity == null) {
            System.out.println("[MoveShip] Ship not found: " + shipEntityId);
            return;
        }

        // 检查是否为舰船类型喵
        if (!(entity instanceof ShipBody)) {
            System.out.println("[MoveShip] Entity is not a ship: " + shipEntityId);
            return;
        }

        ShipBody ship = (ShipBody) entity;

        // 检查舰船是否属于该国家喵
        if (!nationId.equals(ship.ownerNationId)) {
            System.out.println("[MoveShip] Ship does not belong to nation: " + nationId);
            return;
        }

        // 设置目标位置喵
        ship.movementTarget = new Vec2d(targetX, targetY);
        ship.isMoving = true;

        // 计算朝向（从当前位置到目标位置）喵
        double dx = targetX - ship.posWorldGU.x();
        double dy = targetY - ship.posWorldGU.y();
        double headingDeg = Math.toDegrees(Math.atan2(dy, dx));
        ship.targetHeadingDeg = headingDeg;

        System.out.println("[MoveShip] Ship " + shipEntityId + " moving to (" +
                Math.round(targetX) + ", " + Math.round(targetY) + ") heading " +
                Math.round(headingDeg) + "°");
    }
}
