package staraxis.webnet.command;

import staraxis.game.StarAxisGameRuntime;

import java.util.Map;

/**
 * ColonizePlanetWebCommand
 *
 * @description
 *              殖民行星的Web命令处理器喵。
 *              接收前端请求，提交ColonizePlanetCommand到游戏命令队列喵。
 *
 * @usage
 *        - 前端发送 JSON：
 *        - {"type":"colonizePlanet","shipEntityId":123,"planetEntityId":456,"nationId":"player_nation"}
 *        - WebNetServer 中注册：
 *        - commandRegistry.register(new ColonizePlanetWebCommand());
 *
 * @provides
 *           - **命令类型**: type() = "colonizePlanet"
 *           - **殖民命令提交**: 提交ColonizePlanetCommand到游戏命令队列喵
 *
 * @api
 *      - 入参字段：
 *      - shipEntityId: 殖民舰实体ID（必需）
 *      - planetEntityId: 目标行星实体ID（必需）
 *      - nationId: 殖民国家ID（必需）
 *      - 返回：统一 command_response：
 *      {"type":"command_response","ok":true,"command":"colonizePlanet"}
 *      - ok=false: {"type":"command_response","ok":false,"error":"..."}
 *
 * @important_notes
 *                  - 该命令通过game层的命令队列执行，在下一个tick的PrepareTick阶段统一生效喵
 *                  - 命令执行前会进行条件验证（行星无主、距离足够等）喵
 *                  - 殖民成功后，行星所有权将分配给指定国家，星区归属可能更新喵
 */
public class ColonizePlanetWebCommand implements WebCommandHandler {

    @Override
    public String type() {
        return "colonizePlanet";
    }

    @Override
    public String handle(Map<String, Object> message, StarAxisGameRuntime runtime) {
        try {
            // 解析参数喵
            long shipEntityId = 0;
            long planetEntityId = 0;
            String nationId = null;

            Object shipIdObj = message.get("shipEntityId");
            Object planetIdObj = message.get("planetEntityId");
            Object nationIdObj = message.get("nationId");

            if (shipIdObj != null) {
                if (shipIdObj instanceof Number) {
                    shipEntityId = ((Number) shipIdObj).longValue();
                } else {
                    shipEntityId = Long.parseLong(String.valueOf(shipIdObj));
                }
            }

            if (planetIdObj != null) {
                if (planetIdObj instanceof Number) {
                    planetEntityId = ((Number) planetIdObj).longValue();
                } else {
                    planetEntityId = Long.parseLong(String.valueOf(planetIdObj));
                }
            }

            if (nationIdObj != null) {
                nationId = String.valueOf(nationIdObj).trim();
                if (nationId.isEmpty()) {
                    nationId = null;
                }
            }

            // 参数验证喵
            if (shipEntityId <= 0) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"invalid_ship_entity_id\"}";
            }
            if (planetEntityId <= 0) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"invalid_planet_entity_id\"}";
            }
            if (nationId == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"nation_id_required\"}";
            }

            // 提交游戏命令喵
            runtime.getCommandBusForSimOnly().submit(new staraxis.game.command.ColonizePlanetCommand(
                    shipEntityId, planetEntityId, nationId));

            staraxis.webnet.core.WebNetLog.log("cmd colonizePlanet queued: ship=" + shipEntityId +
                    " planet=" + planetEntityId + " nation=" + nationId + " 喵");

            return "{\"type\":\"command_response\",\"ok\":true,\"command\":\"colonizePlanet\"}";
        } catch (Exception e) {
            staraxis.webnet.core.WebNetLog.log("ColonizePlanetWebCommand failed: " + String.valueOf(e));
            return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"command_failed\"}";
        }
    }
}