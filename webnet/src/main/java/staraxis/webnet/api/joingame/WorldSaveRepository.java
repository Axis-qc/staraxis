package staraxis.webnet.api.joingame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WorldSaveRepository（世界存档仓库）喵。
 *
 * 作用：
 * - 统一管理 `gamedata/saves/{worldId}`（世界存档目录）下的 world.json 与 players.json 读写喵。
 */
public final class WorldSaveRepository {

    private final ObjectMapper objectMapper;

    public WorldSaveRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path savesDir() {
        return Paths.get("gamedata", "saves");
    }

    public void ensureSavesDir() throws Exception {
        Files.createDirectories(savesDir());
    }

    public Path worldDir(String worldId) {
        return savesDir().resolve(worldId);
    }

    public Path worldMetaPath(String worldId) {
        return worldDir(worldId).resolve("world.json");
    }

    public Path playersPath(String worldId) {
        return worldDir(worldId).resolve("players.json");
    }

    /**
     * 创建世界目录并保存 world.json（世界元配置）喵。
     */
    public void saveWorldMeta(String worldId, Map<String, Object> worldMeta) throws Exception {
        ensureSavesDir();
        Path dir = worldDir(worldId);
        Files.createDirectories(dir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(worldMetaPath(worldId).toFile(), worldMeta);

        // 初始化 players.json，避免首次读取不存在喵。
        if (!Files.exists(playersPath(worldId))) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(playersPath(worldId).toFile(), Map.of("players", List.of()));
        }
    }

    /**
     * 读取 world.json（世界元配置）喵。
     */
    public Map<String, Object> loadWorldMeta(String worldId) throws Exception {
        Path p = worldMetaPath(worldId);
        if (!Files.exists(p)) {
            return null;
        }
        return objectMapper.readValue(p.toFile(), new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 列举所有世界 world.json（世界元配置）喵。
     */
    public List<Map<String, Object>> listWorldMetas() throws Exception {
        Path dir = savesDir();
        if (!Files.exists(dir)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                if (!Files.isDirectory(p)) {
                    continue;
                }
                String worldId = p.getFileName().toString();
                Map<String, Object> meta = null;
                try {
                    meta = loadWorldMeta(worldId);
                } catch (Exception e) {
                    // 单个世界元数据加载失败时，创建一个最小元数据映射喵。
                    meta = new LinkedHashMap<>();
                    meta.put("worldId", worldId);
                    meta.put("worldName", worldId);
                    meta.put("tickPolicy", "RUN_WHEN_ONLINE");
                    meta.put("createdAtEpochMs", 0L);
                    meta.put("worldRadius", 0);
                    meta.put("worldSeed", "");
                    meta.put("spawnMode", "manual");
                }
                if (meta != null) {
                    list.add(meta);
                }
            }
        } catch (Exception e) {
            // 目录访问异常（如权限不足）时返回空列表喵。
            return new ArrayList<>();
        }
        return list;
    }

    /**
     * 获取指定世界玩家角色列表喵。
     *
     * 返回结构：
     * - players: [{ playerId（玩家账号ID）, roleId（世界内角色ID）, playerState（玩家状态）, nationId（国家ID，可空） }] 喵。
     */
    public List<Map<String, Object>> loadPlayers(String worldId) throws Exception {
        Path p = playersPath(worldId);
        if (!Files.exists(p)) {
            return new ArrayList<>();
        }

        Map<String, Object> root = objectMapper.readValue(p.toFile(), new TypeReference<Map<String, Object>>() {
        });
        Object players = root.get("players");
        if (!(players instanceof List<?> arr)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : arr) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    row.put(String.valueOf(e.getKey()), e.getValue());
                }
                out.add(row);
            }
        }
        return out;
    }

    public void savePlayers(String worldId, List<Map<String, Object>> players) throws Exception {
        Files.createDirectories(worldDir(worldId));
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(playersPath(worldId).toFile(), Map.of("players", players));
    }

    /**
     * upsert 玩家角色记录喵。
     */
    public Map<String, Object> upsertPlayer(String worldId, String playerId, String roleId, String playerState, String nationId)
            throws Exception {
        List<Map<String, Object>> players = loadPlayers(worldId);

        Map<String, Object> hit = null;
        for (Map<String, Object> p : players) {
            String pid = p.get("playerId") == null ? null : String.valueOf(p.get("playerId"));
            if (playerId.equals(pid)) {
                hit = p;
                break;
            }
        }

        if (hit == null) {
            hit = new LinkedHashMap<>();
            hit.put("playerId", playerId);
            hit.put("roleId", roleId == null || roleId.isBlank() ? ("role_" + playerId) : roleId);
            players.add(hit);
        }

        if (playerState != null && !playerState.isBlank()) {
            hit.put("playerState", playerState);
        }
        if (nationId != null) {
            hit.put("nationId", nationId);
        }

        savePlayers(worldId, players);
        return hit;
    }

    public Map<String, Object> getPlayer(String worldId, String playerId) throws Exception {
        List<Map<String, Object>> players = loadPlayers(worldId);
        for (Map<String, Object> p : players) {
            String pid = p.get("playerId") == null ? null : String.valueOf(p.get("playerId"));
            if (playerId.equals(pid)) {
                return p;
            }
        }
        return null;
    }

    /**
     * state.json（最新状态）路径喵。
     */
    public Path statePath(String worldId) {
        return worldDir(worldId).resolve("state.json");
    }

    /**
     * autosave 文件路径喵。
     */
    public Path autoSavePath(String worldId, int slot) {
        return worldDir(worldId).resolve("autosave_" + slot + ".json");
    }

    /**
     * manual save 文件路径喵。
     */
    public Path manualSavePath(String worldId, String saveId) {
        return worldDir(worldId).resolve("manual_" + saveId + ".json");
    }

    /**
     * 写入最新状态 state.json，并可选执行 autosave/manual 落盘喵。
     */
    public void saveState(String worldId, Map<String, Object> state, boolean writeAutoSave, String manualSaveId) throws Exception {
        Files.createDirectories(worldDir(worldId));

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath(worldId).toFile(), state);

        if (writeAutoSave) {
            rotateAndWriteAutoSave(worldId, state);
        }

        if (manualSaveId != null && !manualSaveId.isBlank()) {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(manualSavePath(worldId, manualSaveId).toFile(), state);
        }
    }

    /**
     * autosave 轮转：最多 4 个，新的顶替最旧的喵。
     */
    private void rotateAndWriteAutoSave(String worldId, Map<String, Object> state) throws Exception {
        List<Path> autos = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Path p = autoSavePath(worldId, i);
            if (Files.exists(p)) {
                autos.add(p);
            }
        }

        if (autos.size() < 4) {
            int nextSlot = autos.size() + 1;
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(autoSavePath(worldId, nextSlot).toFile(), state);
            return;
        }

        Path oldest = autos.stream()
                .min(Comparator.comparingLong(this::lastModifiedSafe))
                .orElse(autoSavePath(worldId, 1));

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(oldest.toFile(), state);
    }

    private long lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 列举指定世界下的 autosave 与 manual 存档文件喵。
     */
    public List<Map<String, Object>> listStateSaves(String worldId) throws Exception {
        Path dir = worldDir(worldId);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }

                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".json")) {
                    continue;
                }
                if (!(name.startsWith("autosave_") || name.startsWith("manual_") || name.equals("state.json"))) {
                    continue;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("fileName", p.getFileName().toString());
                item.put("saveType", name.startsWith("autosave_") ? "auto" : (name.startsWith("manual_") ? "manual" : "latest"));
                item.put("path", p.toString());
                item.put("lastModifiedEpochMs", Files.getLastModifiedTime(p).toMillis());
                item.put("sizeBytes", Files.size(p));
                out.add(item);
            }
        }

        out.sort((a, b) -> {
            long ta = a.get("lastModifiedEpochMs") instanceof Number n ? n.longValue() : 0L;
            long tb = b.get("lastModifiedEpochMs") instanceof Number n ? n.longValue() : 0L;
            return Long.compare(tb, ta);
        });
        return out;
    }

    /**
     * 按类型解析目标存档文件喵。
     */
    public Path resolveStatePath(String worldId, String loadType, String fileName) {
        String t = loadType == null ? "latest" : loadType.trim().toLowerCase(Locale.ROOT);
        if ("latest".equals(t) || t.isBlank()) {
            return statePath(worldId);
        }
        if ("auto".equals(t)) {
            if (fileName != null && !fileName.isBlank()) {
                return worldDir(worldId).resolve(fileName);
            }
            return autoSavePath(worldId, 1);
        }
        if ("manual".equals(t)) {
            if (fileName != null && !fileName.isBlank()) {
                return worldDir(worldId).resolve(fileName);
            }
            return manualSavePath(worldId, "default");
        }
        return statePath(worldId);
    }

    /**
     * 读取指定存档文件内容喵。
     */
    public Map<String, Object> loadStateFile(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        Object o = objectMapper.readValue(path.toFile(), Object.class);
        if (o instanceof Map<?, ?> mapObj) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : mapObj.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        return null;
    }

    /**
     * 删除世界存档目录及其所有内容喵。
     *
     * @param worldId 世界ID
     * @return 是否成功删除
     */
    public boolean deleteWorld(String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return false;
        }
        Path dir = worldDir(worldId);
        if (!Files.exists(dir)) {
            return true; // 目录不存在视为已删除
        }
        try {
            // 递归删除目录及其内容喵
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder()) // 先删除文件，再删除目录
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            // 忽略单个文件删除失败，继续删除其他文件喵
                        }
                    });
            return !Files.exists(dir);
        } catch (Exception e) {
            return false;
        }
    }
}



