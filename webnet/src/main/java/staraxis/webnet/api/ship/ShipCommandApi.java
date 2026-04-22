package staraxis.webnet.api.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.MoveShipCommand;
import staraxis.game.entity.Entity;
import staraxis.game.ship.MovementCommand;
import staraxis.game.ship.ShipBody;
import staraxis.game.state.WorldState;
import staraxis.game.world.Vec2d;
import staraxis.webnet.game.GameSessions;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ShipCommandApi {

    private static final double COMPLETION_POSITION_TOLERANCE_GU = 20.0;

    private ShipCommandApi() {
    }

    public static Map<String, Object> handleMoveShip(
            ObjectMapper objectMapper,
            String worldId,
            Map<String, Object> req) {
        String nationId = req.get("nationId") == null ? null : String.valueOf(req.get("nationId"));
        String clientCommandId = req.get("clientCommandId") == null ? null : String.valueOf(req.get("clientCommandId"));
        Long shipEntityId = req.get("shipEntityId") instanceof Number n ? n.longValue() : null;
        Double targetX = req.get("targetX") instanceof Number n ? n.doubleValue() : null;
        Double targetY = req.get("targetY") instanceof Number n ? n.doubleValue() : null;

        if (nationId == null || nationId.isBlank()) {
            return Map.of("ok", false, "error", "nationId_required");
        }
        if (clientCommandId == null || clientCommandId.isBlank()) {
            return Map.of("ok", false, "error", "clientCommandId_required");
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

        runtime.submitCommand(new MoveShipCommand(nationId, clientCommandId, shipEntityId, targetX, targetY));

        return Map.of(
                "ok", true,
                "status", "submitted",
                "message", "move_command_submitted",
                "clientCommandId", clientCommandId,
                "shipEntityId", shipEntityId,
                "target", Map.of("x", targetX, "y", targetY));
    }

    public static Map<String, Object> handleMoveShipCompletion(
            ObjectMapper objectMapper,
            String worldId,
            Map<String, Object> req) {
        String nationId = req.get("nationId") == null ? null : String.valueOf(req.get("nationId"));
        String clientCommandId = req.get("clientCommandId") == null ? null : String.valueOf(req.get("clientCommandId"));
        Long shipEntityId = req.get("shipEntityId") instanceof Number n ? n.longValue() : null;
        Double reportedGameSeconds = req.get("reportedGameSeconds") instanceof Number n ? n.doubleValue() : null;
        Vec2d reportedPosition = parseVec2(req.get("reportedPosition"));

        if (nationId == null || nationId.isBlank()) {
            return Map.of("ok", false, "error", "nationId_required");
        }
        if (clientCommandId == null || clientCommandId.isBlank()) {
            return Map.of("ok", false, "error", "clientCommandId_required");
        }
        if (shipEntityId == null) {
            return Map.of("ok", false, "error", "shipEntityId_required");
        }
        if (reportedGameSeconds == null) {
            return Map.of("ok", false, "error", "reportedGameSeconds_required");
        }
        if (reportedPosition == null) {
            return Map.of("ok", false, "error", "reportedPosition_required");
        }

        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        WorldState worldState = runtime.getWorldStateForSimOnly();
        Entity entity = worldState.entitiesById.get(shipEntityId);
        if (!(entity instanceof ShipBody ship)) {
            return Map.of("ok", false, "error", "ship_not_found");
        }
        if (!nationId.equals(ship.ownerNationId)) {
            return Map.of("ok", false, "error", "ship_not_owned_by_nation");
        }

        long authoritativeTick = worldState.time.simulationTick;
        double authoritativeGameSeconds = worldState.time.totalGameSecondsAcc;
        boolean matchesActive = clientCommandId.equals(ship.activeClientCommandId);
        boolean matchesCompleted = clientCommandId.equals(ship.lastCompletedClientCommandId);
        double distanceToAuthoritative = distance(ship.posWorldGU, reportedPosition);

        if (matchesCompleted && distanceToAuthoritative <= COMPLETION_POSITION_TOLERANCE_GU) {
            return Map.of(
                    "ok", true,
                    "status", "completed",
                    "clientCommandId", clientCommandId,
                    "shipEntityId", shipEntityId,
                    "authoritativeTick", authoritativeTick,
                    "gameSeconds", authoritativeGameSeconds);
        }

        String reason;
        if (matchesActive && ship.movementCommand != null) {
            reason = "authoritative_command_still_running";
        } else if (matchesCompleted) {
            reason = "completion_position_mismatch";
        } else {
            reason = "command_id_mismatch";
        }

        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("status", "corrected");
        response.put("clientCommandId", clientCommandId);
        response.put("shipEntityId", shipEntityId);
        response.put("authoritativeTick", authoritativeTick);
        response.put("gameSeconds", authoritativeGameSeconds);
        response.put("reason", reason);
        response.put("correctionData", buildCorrectionData(ship));
        return response;
    }

    private static Vec2d parseVec2(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Object xRaw = map.get("x");
        Object yRaw = map.get("y");
        if (!(xRaw instanceof Number xNumber) || !(yRaw instanceof Number yNumber)) {
            return null;
        }
        return new Vec2d(xNumber.doubleValue(), yNumber.doubleValue());
    }

    private static double distance(Vec2d a, Vec2d b) {
        if (a == null || b == null) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static Map<String, Object> buildCorrectionData(ShipBody ship) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("position", toVec2Map(ship.posWorldGU));
        data.put("velocity", toVec2Map(ship.velWorldGU));
        data.put("headingDeg", ship.currentHeadingDeg);
        data.put("movementCommand", toMovementCommandMap(ship.movementCommand));
        return data;
    }

    private static Map<String, Object> toVec2Map(Vec2d value) {
        if (value == null) {
            return null;
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("x", value.x());
        result.put("y", value.y());
        return result;
    }

    private static Map<String, Object> toMovementCommandMap(MovementCommand command) {
        if (command == null) {
            return null;
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("commandType", toMovementCommandType(command.commandType));
        result.put("clientCommandId", command.clientCommandId);
        result.put("targetPosition", toVec2Map(command.targetPosition));
        result.put("startPosition", toVec2Map(command.startPosition));
        result.put("startVelocity", toVec2Map(command.startVelocity));
        result.put("startHeadingDeg", command.startHeadingDeg);
        result.put("startGameSeconds", command.startGameSeconds);
        result.put("startSimulationTick", command.startSimulationTick);
        result.put("maxSpeed", command.maxSpeed);
        result.put("baseAcceleration", command.baseAcceleration);
        result.put("bowAccelerationBonus", command.bowAccelerationBonus);
        result.put("turnRate", command.turnRate);
        result.put("lateralSpeedPenalty", command.lateralSpeedPenalty);
        result.put("reverseSpeedPenalty", command.reverseSpeedPenalty);
        return result;
    }

    private static String toMovementCommandType(int commandType) {
        if (commandType == MovementCommand.TYPE_MOVE_TO) {
            return "MOVE_TO";
        }
        if (commandType == MovementCommand.TYPE_STOP) {
            return "STOP";
        }
        return "UNKNOWN";
    }
}
