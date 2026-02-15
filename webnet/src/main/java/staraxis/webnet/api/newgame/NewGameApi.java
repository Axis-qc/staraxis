package staraxis.webnet.api.newgame;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.world.WorldGenConfig;
import staraxis.webnet.game.GameSessions;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NewGameApi
 *
 * 作用：
 * - 提供新游戏三步流程（step1/step2/step3）的 HTTP handler 逻辑。
 *
 * 注意：
 * - 文件 IO 属于阻塞操作：路由层必须使用 exchange.dispatch(...)。
 */
public final class NewGameApi {

    private NewGameApi() {
    }

    /**
     * Step 1：选择国家并写入草稿。
     */
    public static Map<String, Object> step1SelectNation(ObjectMapper objectMapper, NewGameDraftRepository repo,
            Map<String, Object> req) {
        String username = req.get("username") == null ? null : String.valueOf(req.get("username")).trim();
        String playerId = req.get("playerId") == null ? null : String.valueOf(req.get("playerId")).trim();
        String nationId = req.get("nationId") == null ? null : String.valueOf(req.get("nationId")).trim();

        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("nationId_required");
        }

        NewGameDraft d = repo.load(username, playerId);
        if (d == null) {
            d = new NewGameDraft();
            d.schemaVersion = 1;
            d.username = username;
            d.playerId = playerId;
        }
        d.nationId = nationId;
        repo.save(d);

        return Map.of(
                "ok", true,
                "newGameDraftId", username);
    }

    /**
     * Step 2：保存 worldGenConfig 到草稿。
     */
    public static Map<String, Object> step2WorldSettings(ObjectMapper objectMapper, NewGameDraftRepository repo,
            Map<String, Object> req) {
        String username = req.get("username") == null ? null : String.valueOf(req.get("username")).trim();
        String playerId = req.get("playerId") == null ? null : String.valueOf(req.get("playerId")).trim();
        String newGameDraftId = req.get("newGameDraftId") == null ? null
                : String.valueOf(req.get("newGameDraftId")).trim();

        if (newGameDraftId == null || newGameDraftId.isBlank() || !newGameDraftId.equals(username)) {
            throw new IllegalArgumentException("newGameDraftId_invalid");
        }

        Object wgc = req.get("worldGenConfig");
        if (!(wgc instanceof Map)) {
            throw new IllegalArgumentException("worldGenConfig_required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> worldGenConfig = (Map<String, Object>) wgc;

        NewGameDraft d = repo.load(username, playerId);
        if (d == null) {
            d = new NewGameDraft();
            d.schemaVersion = 1;
            d.username = username;
            d.playerId = playerId;
        }

        d.worldGenConfig = new LinkedHashMap<>(worldGenConfig);
        repo.save(d);

        return Map.of("ok", true);
    }

    /**
     * Step 3：确认开始生成（本期仅校验草稿存在与字段齐全，后续接入 game 世界生成）。
     */
    public static Map<String, Object> step3Confirm(ObjectMapper objectMapper, NewGameDraftRepository repo,
            Map<String, Object> req) {
        String username = req.get("username") == null ? null : String.valueOf(req.get("username")).trim();
        String playerId = req.get("playerId") == null ? null : String.valueOf(req.get("playerId")).trim();
        String newGameDraftId = req.get("newGameDraftId") == null ? null
                : String.valueOf(req.get("newGameDraftId")).trim();

        if (newGameDraftId == null || newGameDraftId.isBlank() || !newGameDraftId.equals(username)) {
            throw new IllegalArgumentException("newGameDraftId_invalid");
        }

        NewGameDraft d = repo.load(username, playerId);
        if (d == null) {
            throw new IllegalArgumentException("draft_not_found");
        }
        if (d.nationId == null || d.nationId.isBlank()) {
            throw new IllegalArgumentException("nationId_required");
        }
        if (d.worldGenConfig == null || d.worldGenConfig.isEmpty()) {
            throw new IllegalArgumentException("worldGenConfig_required");
        }

        // 接入 game 世界生成（当前为同步生成 + webnet 内存会话存储）
        WorldGenConfig cfg = new WorldGenConfig();
        // 注入玩家国家定义喵
        staraxis.game.nation.NationDef nationDef = new staraxis.game.nation.NationDef();
        nationDef.id = d.nationId;
        // name/description 目前由前端/预设决定：若草稿未携带则回退为 nationId 喵
        Object nationName = d.worldGenConfig.get("nationName");
        nationDef.name = nationName == null ? d.nationId : String.valueOf(nationName);
        Object nationDesc = d.worldGenConfig.get("nationDescription");
        nationDef.description = nationDesc == null ? "" : String.valueOf(nationDesc);

        // 出生点策略（新游戏）：preset/random 喵
        Object spawnMode = d.worldGenConfig.get("spawnMode");
        if (spawnMode != null && !String.valueOf(spawnMode).isBlank()) {
            String m = String.valueOf(spawnMode).trim();
            if (staraxis.game.nation.NationDef.SpawnStrategy.MODE_PRESET.equals(m)
                    || staraxis.game.nation.NationDef.SpawnStrategy.MODE_RANDOM.equals(m)) {
                nationDef.spawnStrategy.mode = m;
            }
        }
        Object spawnPresetId = d.worldGenConfig.get("spawnPresetId");
        if (spawnPresetId != null && !String.valueOf(spawnPresetId).isBlank()) {
            nationDef.spawnStrategy.presetSystemId = String.valueOf(spawnPresetId).trim();
        }

        cfg.playerNationDef = nationDef;

        Object radius = d.worldGenConfig.get("worldRadius");
        if (radius instanceof Number) {
            cfg.worldRadius = ((Number) radius).intValue();
        } else if (radius != null) {
            try {
                cfg.worldRadius = Integer.parseInt(String.valueOf(radius));
            } catch (Exception e) {
                throw new IllegalArgumentException("worldRadius_invalid");
            }
        } else {
            throw new IllegalArgumentException("worldRadius_required");
        }

        Object seed = d.worldGenConfig.get("worldSeed");
        cfg.worldSeed = seed == null ? null : String.valueOf(seed);

        Object shape = d.worldGenConfig.get("galaxyShape");
        cfg.galaxyShape = shape == null ? null : String.valueOf(shape);

        Object worldType = d.worldGenConfig.get("worldType");
        if (worldType != null && !String.valueOf(worldType).isBlank()) {
            try {
                cfg.worldType = staraxis.game.world.WorldType.valueOf(String.valueOf(worldType).trim());
            } catch (Exception ignored) {
                cfg.worldType = staraxis.game.world.WorldType.SINGLE_PLAYER;
            }
        } else {
            cfg.worldType = staraxis.game.world.WorldType.SINGLE_PLAYER;
        }

        staraxis.webnet.core.WebNetLog.initTruncate();
        staraxis.webnet.core.WebNetLog
                .log("NewGameApi.step3Confirm begin username=" + username + " playerId=" + playerId);

        StarAxisGameRuntime runtime = StarAxisGameRuntime.newGame(cfg);

        // 确保国家已注册并绑定玩家喵
        var nm = runtime.getWorldStateForSimOnly().nationManager;
        if (!nm.hasNation(d.nationId)) {
            nm.registerNation(d.nationId);
        }
        nm.assignPlayerToNation(playerId, d.nationId);

        runtime.start();

        // 单世界：覆盖当前运行时
        GameSessions.setRuntime(runtime);
        staraxis.webnet.core.WebNetLog.log("NewGameApi.step3Confirm setRuntime ok worldRadius=" + cfg.worldRadius);

        return Map.of(
                "ok", true,
                "gameSessionId", "single_world");
    }

    /**
     * 解析 JSON body 为 Map。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseBodyToMap(ObjectMapper objectMapper, String body) throws Exception {
        if (body == null || body.isBlank()) {
            body = "{}";
        }
        Object o = objectMapper.readValue(body, Map.class);
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return Map.of();
    }

    /**
     * JSON Content-Type。
     */
    public static String jsonContentType() {
        return "application/json; charset=" + StandardCharsets.UTF_8.name().toLowerCase();
    }
}
