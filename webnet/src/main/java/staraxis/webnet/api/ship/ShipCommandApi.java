package staraxis.webnet.api.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.MoveShipCommand;
import staraxis.webnet.game.GameSessions;

import java.util.Map;

public final class ShipCommandApi {

    private ShipCommandApi() {
    }

    public static Map<String, Object> handleMoveShip(
            ObjectMapper objectMapper,
            String worldId,
            Map<String, Object> req) {
        String nationId = req.get("nationId") == null ? null : String.valueOf(req.get("nationId"));
        Long shipEntityId = req.get("shipEntityId") instanceof Number n ? n.longValue() : null;
        Double targetX = req.get("targetX") instanceof Number n ? n.doubleValue() : null;
        Double targetY = req.get("targetY") instanceof Number n ? n.doubleValue() : null;

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

        runtime.submitCommand(new MoveShipCommand(nationId, shipEntityId, targetX, targetY));

        return Map.of(
                "ok", true,
                "message", "move_command_submitted",
                "shipEntityId", shipEntityId,
                "target", Map.of("x", targetX, "y", targetY));
    }
}
