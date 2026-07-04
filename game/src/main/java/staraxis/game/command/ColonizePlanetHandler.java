package staraxis.game.command;

import staraxis.game.state.WorldState;
import staraxis.game.entity.Entity;
import staraxis.game.space.SpacePosition;
import staraxis.game.ship.ShipBody;

/**
 * ColonizePlanetHandler
 *
 * @description
 *              ColonizePlanetCommand 的处理器，在模拟 tick 内执行殖民逻辑喵。
 *
 *              作用：
 *              - 验证殖民条件：行星无主、殖民舰属于指定国家、距离足够等喵
 *              - 执行殖民操作：将行星所有权分配给指定国家喵
 *              - 更新星区归属（如果该行星是星区内第一个被殖民的实体）喵
 *              - 处理殖民舰状态变化（如消耗殖民舰或改变其状态）喵
 *
 * @important_notes
 *                  - 殖民操作需要严格的条件验证，避免非法殖民喵
 *                  - 殖民成功后需要更新国家资产管理和情报系统喵
 *                  - 星区归属逻辑基于"星区内至少有一个归属实体"的原则喵
 */
public class ColonizePlanetHandler implements CommandHandler<ColonizePlanetCommand> {

    /** 最大殖民距离：殖民舰需要在行星的多少GU距离内才能殖民喵 */
    private static final double COLONIZATION_MAX_DISTANCE_GU = 1000.0; // 距离行星1000GU喵

    @Override
    public void handle(ColonizePlanetCommand command, WorldState worldState, double dtGameHours) throws Exception {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        if (worldState == null) {
            throw new IllegalArgumentException("world_state_required");
        }

        long shipEntityId = command.getShipEntityId();
        long planetEntityId = command.getPlanetEntityId();
        String nationId = command.getNationId();

        // 1. 验证参数合法性喵
        if (shipEntityId <= 0 || planetEntityId <= 0 || nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("invalid_colonization_parameters");
        }

        // 2. 获取实体喵
        Entity shipEntity = worldState.entitiesById.get(shipEntityId);
        Entity planetEntity = worldState.entitiesById.get(planetEntityId);

        if (shipEntity == null) {
            throw new IllegalArgumentException("ship_entity_not_found");
        }
        if (planetEntity == null) {
            throw new IllegalArgumentException("planet_entity_not_found");
        }

        // 3. 验证殖民舰类型喵
        if (shipEntity.entityType != staraxis.game.entity.EntityType.SHIP) {
            throw new IllegalArgumentException("ship_is_not_colony_ship");
        }

        // 4. 验证行星类型喵
        if (planetEntity.entityType != staraxis.game.entity.EntityType.PLANET) {
            throw new IllegalArgumentException("target_is_not_planet");
        }

        // 5. 验证行星是否无主喵
        if (planetEntity.ownerNationId != null && !planetEntity.ownerNationId.isBlank()) {
            throw new IllegalArgumentException("planet_already_owned");
        }

        // 6. 验证殖民舰所有权喵
        if (!nationId.equals(shipEntity.ownerNationId)) {
            throw new IllegalArgumentException("ship_not_owned_by_nation");
        }

        // 7. 验证距离喵
        if (shipEntity.posWorldGU == null || planetEntity.posWorldGU == null) {
            throw new IllegalArgumentException("entity_position_missing");
        }

        double distance = shipEntity.posWorldGU.distanceTo(planetEntity.posWorldGU);

        // 验证距离是否在允许的殖民范围内喵（1000GU）喵
        if (distance > COLONIZATION_MAX_DISTANCE_GU) {
            throw new IllegalArgumentException("ship_too_far_from_planet");
        }

        // 8. 执行殖民操作喵
        // 8.1 分配行星所有权喵
        planetEntity.ownerNationId = nationId;

        // 8.2 通过资产管理器注册资产归属喵
        worldState.nationAssetManager.assignEntityToNation(planetEntityId, nationId);

        // 8.3 更新星系归属标记（简化：systemId 关联的星系默认为该国势力范围）
        if (planetEntity.systemId > 0) {
            staraxis.game.log.GameLog.log("Colonization: system " + planetEntity.systemId +
                    " claimed for " + nationId + " 喵");
        }

        // 8.4 首都绑定：若该国尚未有首都，则将首次殖民行星设为首都喵
        staraxis.game.nation.NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState != null && nationState.capitalPlanetEntityId <= 0) {
            nationState.capitalPlanetEntityId = planetEntityId;
        }

        // 8.5 殖民舰状态处理：殖民成功后消耗殖民舰并同步资产关系喵
        if (shipEntity instanceof ShipBody) {
            worldState.nationAssetManager.releaseEntityOwnership(shipEntityId);
            worldState.entitiesById.remove(shipEntityId);

            staraxis.game.log.GameLog.log("Colonization successful: ship=" + shipEntityId +
                    " colonized planet=" + planetEntityId + " for nation=" + nationId + " and consumed ship 喵");
        }

        // 10. 标记低频基线快照为脏以触发推送喵
        worldState.baselineDirty = true;
    }

}