package staraxis.webnet.api.joingame;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.JoinGameCommand;
import staraxis.game.entity.EntityType;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.webnet.game.GameSessions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JoinGameApi
 *
 * 作用：
 * - 提供“世界已生成时（中途加入）”的出生点选择相关 API。
 *
 * API 由 WebNetServer 负责挂载路由：
 * - GET /api/join-game/available-spawns
 * - POST /api/join-game/confirm-spawn (body JSON)
 *
 * 约束：
 * - 前端必须通过 webnet 请求，不允许直接读取 game/assets 资源或直接访问 game 内部对象。
 * - 星系归属判定口径：只要该 system 的 barycenter/star/planet 任一 ownerNationId
 * 非空，即视为已归属，不可作为出生点。
 */
public final class JoinGameApi {

    private JoinGameApi() {
    }

    /**
     * JSON Content-Type。
     */
    public static void setJsonContentType(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                "application/json; charset=" + StandardCharsets.UTF_8.name().toLowerCase());
    }

    /**
     * 解析 query 参数。
     */
    public static String query(HttpServerExchange exchange, String key) {
        if (exchange == null || key == null) {
            return null;
        }
        Deque<String> v = exchange.getQueryParameters().get(key);
        if (v == null || v.isEmpty()) {
            return null;
        }
        String s = v.peekFirst();
        return s == null ? null : s.trim();
    }

    /**
     * GET /api/join-game/available-spawns
     */
    public static Map<String, Object> handleAvailableSpawns() {
        return handleAvailableSpawns(null);
    }

    /**
     * GET /api/join-game/available-spawns?worldId=...
     */
    public static Map<String, Object> handleAvailableSpawns(String worldId) {
        StarAxisGameRuntime runtime = (worldId == null || worldId.isBlank())
                ? GameSessions.getRuntime()
                : GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of(
                    "ok", false,
                    "error", "no_game_runtime");
        }

        ArrayList<Map<String, Object>> arr = new ArrayList<>();
        var ds = runtime.getDailySettlementStateBufferForReadonly().getActive();
        if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
            for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
                long systemId = Long.parseLong(entry.getKey());
                // 检查星系是否无主：所有基线实体都没有 ownerNationId
                boolean unowned = true;
                int starCount = 0, planetCount = 0;
                double cx = 0, cy = 0;
                for (EntitySnapshot snap : entry.getValue()) {
                    if (snap == null) continue;
                    if (snap.ownerNationId != null) unowned = false;
                    if (snap.entityType == EntityType.STAR) starCount++;
                    if (snap.entityType == EntityType.PLANET) planetCount++;
                    if (snap.posWorldGU != null && cx == 0 && cy == 0) {
                        cx = snap.posWorldGU.x();
                        cy = snap.posWorldGU.z();
                    }
                }
                if (!unowned) continue;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("systemId", systemId);
                item.put("centerX", cx);
                item.put("centerY", cy);
                item.put("starCount", starCount);
                item.put("planetCount", planetCount);
                arr.add(item);
            }
        }

        return Map.of(
                "ok", true,
                "systems", arr);
    }

    /**
     * POST /api/join-game/confirm-spawn
     */
    public static Map<String, Object> handleConfirmSpawn(ObjectMapper objectMapper, Map<String, Object> req) {
        String worldId = req.get("worldId") == null ? null : String.valueOf(req.get("worldId")).trim();
        StarAxisGameRuntime runtime = (worldId == null || worldId.isBlank())
                ? GameSessions.getRuntime()
                : GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return Map.of(
                    "ok", false,
                    "error", "no_game_runtime");
        }

        if (worldId != null && !worldId.isBlank()) {
            GameSessions.setActiveWorld(worldId);
        }

        String playerId = req.get("playerId") == null ? null : String.valueOf(req.get("playerId")).trim();
        Object randomSpawnObj = req.get("randomSpawn");
        boolean randomSpawn = Boolean.TRUE.equals(randomSpawnObj)
                || (randomSpawnObj != null && "true".equalsIgnoreCase(String.valueOf(randomSpawnObj)));

        Object chosen = req.get("chosenSystemId");
        long chosenSystemId;
        if (randomSpawn) {
            chosenSystemId = -1L;
        } else if (chosen instanceof Number) {
            chosenSystemId = ((Number) chosen).longValue();
        } else if (chosen != null) {
            try {
                chosenSystemId = Long.parseLong(String.valueOf(chosen));
            } catch (Exception e) {
                return Map.of(
                        "ok", false,
                        "error", "chosenSystemId_invalid");
            }
        } else {
            return Map.of(
                    "ok", false,
                    "error", "chosenSystemId_required");
        }

        if (playerId == null || playerId.isBlank()) {
            return Map.of(
                    "ok", false,
                    "error", "playerId_required");
        }

        // 使用 JoinGameCommand 在 game 模块内完成星系查找和归属检查喵。
        // TODO AssetManager 统一处理：暂不注册国家/绑定玩家/分配归属/生成舰船，等后续流程设计。
        JoinGameCommand cmd = new JoinGameCommand(playerId, chosenSystemId);
        runtime.executeCommandImmediately(cmd);

        if (!cmd.isSuccess()) {
            return Map.of(
                    "ok", false,
                    "error", cmd.getErrorMessage());
        }

        String nationId = cmd.getNationId();
        long spawnSystemId = cmd.getSpawnSystemId();

        if (worldId != null && !worldId.isBlank()) {
            GameSessions.markPlayerSpawned(worldId, playerId);
            // 更新世界内玩家角色状态为已出生，并记录 nationId 喵
            try {
                WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
                repo.upsertPlayer(worldId, playerId, null, "SPAWNED", nationId);
            } catch (Exception e) {
                return Map.of(
                        "ok", false,
                        "error", "player_registry_update_failed: " + e.getMessage());
            }
        }

        return Map.of(
                "ok", true,
                "nationId", nationId,
                "spawnSystemId", spawnSystemId,
                "worldId", worldId,
                "playerState", (worldId == null || worldId.isBlank()) ? "SPAWNED" : GameSessions.getPlayerState(worldId, playerId));
    }
}
