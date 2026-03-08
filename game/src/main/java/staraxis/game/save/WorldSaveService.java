package staraxis.game.save;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.WorldState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WorldSaveService（世界存档服务）喵。
 *
 * 作用：
 * - 在 game 模块内集中维护世界状态存档口径喵。
 * - 提供自动存档（最多 4 个轮转）与手动存档（无限制）喵。
 *
 * 存档目录：gamedata/saves/{worldId}/ 喵。
 */
public final class WorldSaveService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WorldSaveService() {
    }

    /**
     * 自动存档：写入 state.json，并轮转 autosave_1..4.json 喵。
     */
    public static void saveAuto(StarAxisGameRuntime runtime, String worldId) throws Exception {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime_required");
        }
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId_required");
        }

        Path dir = worldDir(worldId);
        Files.createDirectories(dir);

        Map<String, Object> state = buildState(runtime, worldId, "auto");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(statePath(worldId).toFile(), state);

        rotateAndWriteAutoSave(worldId, state);
    }

    /**
     * 手动存档：写入 state.json 与 manual_*.json（无限制）喵。
     */
    public static String saveManual(StarAxisGameRuntime runtime, String worldId, String saveId) throws Exception {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime_required");
        }
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId_required");
        }

        Path dir = worldDir(worldId);
        Files.createDirectories(dir);

        String finalSaveId = (saveId == null || saveId.isBlank())
                ? ("manual_" + UUID.randomUUID().toString().replace("-", ""))
                : saveId;

        Map<String, Object> state = buildState(runtime, worldId, "manual");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(statePath(worldId).toFile(), state);
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(manualPath(worldId, finalSaveId).toFile(), state);

        return finalSaveId;
    }

    private static Path savesDir() {
        return Paths.get("gamedata", "saves");
    }

    private static Path worldDir(String worldId) {
        return savesDir().resolve(worldId);
    }

    private static Path statePath(String worldId) {
        return worldDir(worldId).resolve("state.json");
    }

    private static Path autoPath(String worldId, int slot) {
        return worldDir(worldId).resolve("autosave_" + slot + ".json");
    }

    private static Path manualPath(String worldId, String saveId) {
        return worldDir(worldId).resolve("manual_" + saveId + ".json");
    }

    /**
     * 自动存档轮转策略：最多保留 4 个，新增覆盖最旧文件喵。
     */
    private static void rotateAndWriteAutoSave(String worldId, Map<String, Object> state) throws Exception {
        List<Path> autos = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Path p = autoPath(worldId, i);
            if (Files.exists(p)) {
                autos.add(p);
            }
        }

        if (autos.size() < 4) {
            int slot = autos.size() + 1;
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(autoPath(worldId, slot).toFile(), state);
            return;
        }

        Path oldest = autos.stream()
                .min(Comparator.comparingLong(WorldSaveService::lastModifiedSafe))
                .orElse(autoPath(worldId, 1));
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(oldest.toFile(), state);
    }

    private static long lastModifiedSafe(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 构建权威状态快照（当前版本）喵。
     */
    private static Map<String, Object> buildState(StarAxisGameRuntime runtime, String worldId, String source) {
        WorldState ws = runtime.getWorldStateForSimOnly();
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("worldId", worldId);
        root.put("source", source);
        root.put("savedAtEpochMs", System.currentTimeMillis());

        Map<String, Object> world = new LinkedHashMap<>();
        world.put("worldRadius", ws.worldMap.radius);
        world.put("simulationTick", rt.simulationTick);
        world.put("totalGameSeconds", rt.totalGameSeconds);
        world.put("deltaGameSeconds", rt.deltaGameSeconds);
        world.put("worldType", rt.worldType == null ? null : rt.worldType.name());
        world.put("gameSecondsPerRealSecond", rt.gameSecondsPerRealSecond);
        world.put("timeScale", rt.timeScale);
        world.put("year", rt.year);
        world.put("month", rt.month);
        world.put("day", rt.day);
        world.put("hour", rt.hour);
        world.put("minute", rt.minute);
        world.put("second", rt.second);
        root.put("world", world);

        // 国家层快照喵。
        List<Map<String, Object>> nations = new ArrayList<>();
        for (var ns : ws.nationManager.getAllNationStates()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("nationId", ns.nationId);
            n.put("name", ns.name);
            n.put("governmentId", ns.governmentId);
            n.put("spawnSystemEntityId", ns.spawnSystemEntityId);
            n.put("capitalPlanetEntityId", ns.capitalPlanetEntityId);
            n.put("playerIds", new ArrayList<>(ns.playerIds));
            nations.add(n);
        }
        root.put("nations", nations);

        return root;
    }
}
