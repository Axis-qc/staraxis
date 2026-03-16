package staraxis.webnet.api.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.MoveShipCommand;
import staraxis.webnet.game.GameSessions;

import java.util.Map;

/**
 * ShipCommandApi（舰船指令 API）喵。
 *
 * 提供舰船相关的命令接口喵。
 */
public final class ShipCommandApi {

    private ShipCommandApi() {
    }

    /**
     * POST /api/ship/move
     * 移动舰船到指定位置喵。
     */
    public static Map<String, Object> handleMoveShip(ObjectMapper objectMapper,
                                                      String worldId,
                                                      Map<String, Object> req) {
        long apiStartTime = System.currentTimeMillis();

        String nationId = req.get("nationId") == null ? null : String.valueOf(req.get("nationId"));
        Long shipEntityId = req.get("shipEntityId") instanceof Number n ? n.longValue() : null;
        Double targetX = req.get("targetX") instanceof Number n ? n.doubleValue() : null;
        Double targetY = req.get("targetY") instanceof Number n ? n.doubleValue() : null;

        System.out.println("[MoveShip-Trace] API接收 ship=" + shipEntityId + " 目标=(" + Math.round(targetX != null ? targetX : 0) + "," + Math.round(targetY != null ? targetY : 0) + ") 时间=" + apiStartTime);

        if (nationId == null || nationId.isBlank()) {
            return Map.of("ok", false, "error", "nationId_required");
        }
        if (shipEntityId == null) {
            return Map.of("ok", false, "error", "shipEntityId_required");
        }
        if (targetX == null || targetY == null) {
            return Map.of("ok", false, "error", "target_coordinates_required");
        }

        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        // 提交移动命令到游戏层执行喵
        MoveShipCommand command = new MoveShipCommand(nationId, shipEntityId, targetX, targetY);
        runtime.submitCommand(command);

        long apiEndTime = System.currentTimeMillis();
        System.out.println("[MoveShip-Trace] API提交命令 ship=" + shipEntityId + " 耗时=" + (apiEndTime - apiStartTime) + "ms");

        return Map.of(
                "ok", true,
                "message", "move_command_submitted",
                "shipEntityId", shipEntityId,
                "target", Map.of("x", targetX, "y", targetY)
        );
    }
}
