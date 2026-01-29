package staraxis.webnet.api.nation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import staraxis.game.nation.NationDef;
import staraxis.game.nation.design.PlayerNationDesign;
import staraxis.webnet.repo.nation.PlayerNationFileRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PlayerNationApi
 *
 * 作用：
 * - 提供玩家自定义国家（Players）的 HTTP API 辅助方法。
 *
 * API 由 WebNetServer 负责挂载路由：
 * - GET /api/nations/players/list?username=...&playerId=...
 * - GET /api/nations/players/get?username=...&playerId=...&nationId=...
 * - POST /api/nations/players/save (body JSON)
 */
public final class PlayerNationApi {

    private PlayerNationApi() {
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
     * GET /api/nations/players/list
     */
    public static String handleList(ObjectMapper objectMapper, PlayerNationFileRepository repo,
            String username, String playerId) throws Exception {
        List<String> ids = repo.listNationIds(username, playerId);
        ArrayList<String> out = new ArrayList<>(ids);
        return objectMapper.writeValueAsString(Map.of(
                "ok", true,
                "nationIds", out));
    }

    /**
     * GET /api/nations/players/get
     */
    public static String handleGet(ObjectMapper objectMapper, PlayerNationFileRepository repo,
            String username, String playerId, String nationId) throws Exception {
        Optional<PlayerNationDesign> d = repo.load(username, playerId, nationId);
        if (d.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of(
                    "ok", false,
                    "error", "not_found"));
        }
        PlayerNationDesign design = d.get();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("design", Map.of(
                "schemaVersion", design.schemaVersion,
                "username", design.username,
                "playerId", design.playerId,
                "updatedAtUnixMs", design.updatedAtUnixMs,
                "nation", PlayerNationFileRepository.toNationItem(design)));
        return objectMapper.writeValueAsString(resp);
    }

    /**
     * POST /api/nations/players/save
     */
    public static String handleSave(ObjectMapper objectMapper, PlayerNationFileRepository repo, String bodyJson)
            throws Exception {
        if (bodyJson == null || bodyJson.isBlank()) {
            bodyJson = "{}";
        }
        PlayerNationDesign design = objectMapper.readValue(bodyJson, PlayerNationDesign.class);
        repo.save(design);
        NationDef n = design.nation;
        return objectMapper.writeValueAsString(Map.of(
                "ok", true,
                "nationId", n == null ? "" : (n.id == null ? "" : n.id)));
    }
}
