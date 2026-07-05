/*
 * ShipMovementSystem
 *
 * 文件作用：
 * - 舰船移动分发系统，遍历全世界的舰船，根据状态路由到对应的子系统。
 * - 二模式路由：自由移动（直飞）→ 惯性滑行/停止。
 *
 * 使用方式：
 * - 在 StarAxisGameRuntime.update() 的 tick pipeline 中调用。
 *
 * 路由规则：
 * 1. isMoving && movementTarget != null → ShipFullMovementSystem（直飞）
 * 2. 否则 → 减速停止或惯性滑行
 *
 * 注意事项：
 * - 在途实体（systemId=0）已被 entityIdsBySystem 排除，不会出现在遍历中。
 * - 非活跃星系不做任何位置更新，切换回时重新从 entitiesById 读取最新位置。
 */

package staraxis.game.ship;

import staraxis.game.entity.Entity;
import staraxis.game.state.WorldState;

/**
 * ShipMovementSystem（舰船移动分发系统）。
 *
 * 二模式路由：直飞模式 → 停止/滑行。
 *
 * 性能策略：
 * - 直接遍历 entitiesById.values() 筛选 ShipBody，不遍历星系嵌套。
 * - 活跃星系（玩家当前查看）：全量逐 tick 计算。
 * - 非活跃星系：跳过所有计算，零开销。
 */
public class ShipMovementSystem {

    private final ShipFullMovementSystem freeMoveSystem = new ShipFullMovementSystem();

    public void update(WorldState worldState, double dtGameHours, long activeSystemId) {
        double dtGameSeconds = dtGameHours * 3600.0;

        for (Entity entity : worldState.entitiesById.values()) {
            if (!(entity instanceof ShipBody ship)) continue;

            if (ship.systemId == activeSystemId) {
                // ═══ 活跃星系：全量计算 ═══
                if (ship.isMoving && ship.movementTarget != null) {
                    freeMoveSystem.updateFreeMove(ship, dtGameSeconds, worldState);
                } else if (ship.velWorldGU != null && ship.velWorldGU.length() > 0) {
                    double nx = ship.posWorldGU.x() + ship.velWorldGU.x() * dtGameSeconds;
                    double ny = ship.posWorldGU.y() + ship.velWorldGU.y() * dtGameSeconds;
                    double nz = ship.posWorldGU.z() + ship.velWorldGU.z() * dtGameSeconds;
                    ship.posWorldGU = new staraxis.game.space.SpacePosition(nx, ny, nz);
                    worldState.markRealtimeDirty();
                }
            }
            // 非活跃星系：跳过所有计算
        }
    }
}
