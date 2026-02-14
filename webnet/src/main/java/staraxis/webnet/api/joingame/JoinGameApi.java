package staraxis.webnet.api.joingame;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
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
        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            return Map.of(
                    "ok", false,
                    "error", "no_game_runtime");
        }

        ArrayList<Map<String, Object>> arr = new ArrayList<>();
        for (StarSystem sys : runtime.getWorldStateForSimOnly().astro.getSystemsView()) {
            if (sys == null) {
                continue;
            }
            if (!isSystemUnowned(sys)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("systemId", sys.systemId);
            item.put("sectorQ", sys.sectorCoord == null ? 0 : sys.sectorCoord.q());
            item.put("sectorR", sys.sectorCoord == null ? 0 : sys.sectorCoord.r());
            item.put("centerX", sys.centerWorldGU == null ? 0 : sys.centerWorldGU.x());
            item.put("centerY", sys.centerWorldGU == null ? 0 : sys.centerWorldGU.y());
            item.put("starCount", sys.stars == null ? 0 : sys.stars.size());
            item.put("planetCount", sys.planets == null ? 0 : sys.planets.size());
            arr.add(item);
        }

        return Map.of(
                "ok", true,
                "systems", arr);
    }

    /**
     * POST /api/join-game/confirm-spawn
     */
    public static Map<String, Object> handleConfirmSpawn(ObjectMapper objectMapper, Map<String, Object> req) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            return Map.of(
                    "ok", false,
                    "error", "no_game_runtime");
        }

        String playerId = req.get("playerId") == null ? null : String.valueOf(req.get("playerId")).trim();
        Object chosen = req.get("chosenSystemId");
        long chosenSystemId;
        if (chosen instanceof Number) {
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

        StarSystem target = null;
        for (StarSystem sys : runtime.getWorldStateForSimOnly().astro.getSystemsView()) {
            if (sys != null && sys.systemId == chosenSystemId) {
                target = sys;
                break;
            }
        }
        if (target == null) {
            return Map.of(
                    "ok", false,
                    "error", "system_not_found");
        }

        if (!isSystemUnowned(target)) {
            return Map.of(
                    "ok", false,
                    "error", "system_already_owned");
        }

        // 自动创建/分配 nationId：使用 playerId 派生稳定 ID 喵
        String nationId = "nation_" + playerId;

        var ws = runtime.getWorldStateForSimOnly();
        var nm = ws.nationManager;
        if (!nm.hasNation(nationId)) {
            nm.registerNation(nationId);
        }
        nm.assignPlayerToNation(playerId, nationId);

        var ns = nm.getNationState(nationId);
        if (ns != null) {
            // 名称暂用 nationId，占位：后续可接入 accounts/玩家昵称喵
            if (ns.name == null || ns.name.isBlank()) {
                ns.name = nationId;
            }
            ns.spawnSystemEntityId = target.systemId;
        }

        // 占用：系统级 owner + 系统内天体 owner 全量落账喵
        target.assignOwnership(nationId);

        return Map.of(
                "ok", true,
                "nationId", nationId,
                "spawnSystemId", target.systemId);
    }

    private static boolean isSystemUnowned(StarSystem sys) {
        if (sys == null) {
            return false;
        }

        if (sys.ownerNationId != null && !sys.ownerNationId.isBlank()) {
            return false;
        }

        if (sys.stars != null) {
            for (StarBody star : sys.stars) {
                if (star != null && star.ownerNationId != null && !star.ownerNationId.isBlank()) {
                    return false;
                }
            }
        }

        if (sys.planets != null) {
            for (PlanetBody planet : sys.planets) {
                if (planet != null && planet.ownerNationId != null && !planet.ownerNationId.isBlank()) {
                    return false;
                }
            }
        }

        return true;
    }
}
