package staraxis.webnet.command;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.space.SpacePosition;
import staraxis.game.world.WorldHexLayout;
import staraxis.game.world.hex.SectorCoord;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SpawnColonyShipCommand
 *
 * @description
 *              生成初始殖民舰的命令处理器喵。
 *              在世界生成后，玩家需要选择位置生成初始殖民舰，用于殖民第一个星球喵。
 *              使用硬编码的基础属性，暂不依赖设计文件喵。
 *
 * @usage
 *        - 前端发送 JSON：
 *        - {"type":"spawnColonyShip","x":123.4,"y":567.8,"nationId":"player_nation"}
 *        - 或 {"type":"spawnColonyShip","sectorQ":1,"sectorR":2,"nationId":"player_nation"}
 *        - WebNetServer 中注册：
 *        - commandRegistry.register(new SpawnColonyShipCommand());
 *
 * @provides
 *           - **命令类型**: type() = "spawnColonyShip"
 *           - **殖民舰生成**: 在指定位置生成殖民舰并分配给当前玩家国家喵
 *
 * @api
 *      - 入参字段：
 *      - nationId: 玩家国家ID（必需）
 *      - 位置参数（二选一）：
 *        - x, y: 世界坐标（GU）
 *        - sectorQ, sectorR: 星区坐标（六边形网格）
 *      - 返回：统一 command_response：
 *      {"type":"command_response","ok":true,"command":"spawnColonyShip","shipEntityId":123}
 *      - ok=false: {"type":"command_response","ok":false,"error":"..."}
 *
 * @important_notes
 *                  - 殖民舰使用硬编码基础属性：耐久1.0，能源100，燃料100，无组件喵
 *                  - 实体ID生成使用简单的静态AtomicLong，未来应改为全局ID生成器喵
 *                  - 生成的殖民舰会立即注册到WorldState，并分配给当前玩家的国家喵
 *                  - 暂不依赖设计文件，后续改为配置驱动喵
 */
public class SpawnColonyShipCommand implements WebCommandHandler {

    @Override
    public String type() {
        return "spawnColonyShip";
    }

    @Override
    public String handle(Map<String, Object> message, StarAxisGameRuntime runtime) {
        try {
            // 获取玩家国家ID喵
            String nationId = null;
            Object nationIdObj = message.get("nationId");
            if (nationIdObj != null) {
                nationId = String.valueOf(nationIdObj).trim();
                if (nationId.isEmpty()) {
                    nationId = null;
                }
            }

            // 如果未提供nationId，尝试从连接中获取（但这里无法获取连接上下文）喵
            // 暂时要求前端必须提供nationId参数喵
            if (nationId == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"nation_id_required\"}";
            }

            // 解析位置参数喵
            staraxis.game.world.Vec2d worldPos2d = null;
            SectorCoord sectorCoord = null;

            Object xObj = message.get("x");
            Object yObj = message.get("y");
            if (xObj != null && yObj != null) {
                // 使用世界坐标参数喵
                double x = xObj instanceof Number ? ((Number) xObj).doubleValue() : Double.parseDouble(String.valueOf(xObj));
                double y = yObj instanceof Number ? ((Number) yObj).doubleValue() : Double.parseDouble(String.valueOf(yObj));
                worldPos2d = new staraxis.game.world.Vec2d(x, y);
                // 计算对应的星区坐标喵
                sectorCoord = WorldHexLayout.worldToSectorCoord(worldPos2d);
            } else {
                // 使用星区坐标参数喵
                Object qObj = message.get("sectorQ");
                Object rObj = message.get("sectorR");
                if (qObj != null && rObj != null) {
                    int sectorQ = qObj instanceof Number ? ((Number) qObj).intValue() : Integer.parseInt(String.valueOf(qObj));
                    int sectorR = rObj instanceof Number ? ((Number) rObj).intValue() : Integer.parseInt(String.valueOf(rObj));
                    sectorCoord = new SectorCoord(sectorQ, sectorR);
                    // 计算星区中心点作为世界坐标喵
                    worldPos2d = WorldHexLayout.sectorCenterWorld2D_GU(sectorCoord);
                }
            }

            if (worldPos2d == null || sectorCoord == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"position_required\"}";
            }

            // 映射到 3D 空间：XZ 平面，Y=0
            SpacePosition worldPos = new SpacePosition(worldPos2d.x(), 0, worldPos2d.y());

            // 使用权威舰船生成服务创建殖民舰喵
            var worldState = runtime.getWorldStateForSimOnly();
            java.util.Set<String> customFlags = new java.util.LinkedHashSet<>();
            customFlags.add("INITIAL_SPAWN_SHIP");
            long shipEntityId = staraxis.game.ship.ShipSpawnService.spawnShipAtPosition(
                    worldState, nationId, worldPos, sectorCoord, 0L, customFlags);

            staraxis.webnet.core.WebNetLog.log("Spawned colony ship entityId=" + shipEntityId +
                    " at (" + worldPos.x() + "," + worldPos.z() + ") in sector [" + sectorCoord.q() + "," + sectorCoord.r() + "] for nation " + nationId + " 喵");

            return "{\"type\":\"command_response\",\"ok\":true,\"command\":\"spawnColonyShip\",\"shipEntityId\":" + shipEntityId + "}";
        } catch (Exception e) {
            staraxis.webnet.core.WebNetLog.log("SpawnColonyShipCommand failed: " + String.valueOf(e));
            return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"command_failed\"}";
        }
    }
}