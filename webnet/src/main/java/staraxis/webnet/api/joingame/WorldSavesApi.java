package staraxis.webnet.api.joingame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.LoadWorldCommand;
import staraxis.game.world.WorldGenConfig;
import staraxis.webnet.game.GameSessions;

/**
 * WorldSavesApi（世界存档接口）喵。
 *
 * 作用：
 * - 提供世界列表、创建世界、加入世界、玩家世界状态查询等存档模式入口喵。
 */
public final class WorldSavesApi {

    private WorldSavesApi() {
    }

    /**
     * 解析玩家角色，未命中时回退 USER 喵。
     */
    public static String resolveRole(staraxis.webnet.auth.AuthStore authStore, String playerId) {
        if (authStore == null) {
            return "USER";
        }
        return authStore.getRoleByPlayerId(playerId);
    }

    /**
     * GET /api/worlds
     */
    public static Map<String, Object> handleListWorlds(ObjectMapper objectMapper, boolean includeAllWorldsForAdmin) {
        try {
            List<Map<String, Object>> worlds = new ArrayList<>();
            WorldSaveRepository repo = new WorldSaveRepository(objectMapper);

            List<Map<String, Object>> metas = repo.listWorldMetas();
            for (Map<String, Object> meta : metas) {
                String worldId = meta.get("worldId") == null ? null : String.valueOf(meta.get("worldId"));
                if (worldId == null || worldId.isBlank()) {
                    continue;
                }

                boolean isActive = worldId.equals(GameSessions.getActiveWorldId());
                // 普通用户仅展示运行中世界；管理员可看全部喵。
                if (!includeAllWorldsForAdmin && !isActive) {
                    continue;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("worldId", worldId);
                item.put("worldName", meta.getOrDefault("worldName", worldId));
                item.put("tickPolicy", meta.getOrDefault("tickPolicy", "RUN_WHEN_ONLINE"));
                item.put("createdAtEpochMs", meta.getOrDefault("createdAtEpochMs", 0));
                item.put("active", isActive);

                StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
                if (runtime != null) {
                    try {
                        item.put("worldRadius", runtime.getRealTimeWorldStateReadonly().worldRadius);
                        item.put("simulationTick", runtime.getRealTimeWorldStateReadonly().simulationTick);
                        item.put("totalGameSeconds", runtime.getRealTimeWorldStateReadonly().totalGameSeconds);
                    } catch (Exception e) {
                        // 如果运行时状态访问失败，回退到元数据喵。
                        Object radius = meta.get("worldRadius");
                        item.put("worldRadius", radius == null ? 0 : radius);
                        item.put("simulationTick", 0);
                        item.put("totalGameSeconds", 0);
                    }
                } else {
                    Object radius = meta.get("worldRadius");
                    item.put("worldRadius", radius == null ? 0 : radius);
                    item.put("simulationTick", 0);
                    item.put("totalGameSeconds", 0);
                }
                worlds.add(item);
            }

            String activeWorldId = GameSessions.getActiveWorldId();
            if (activeWorldId == null) {
                activeWorldId = "";
            }
            return Map.of(
                    "ok", true,
                    "worlds", worlds,
                    "activeWorldId", activeWorldId);
        } catch (Exception e) {
            // 发生异常时返回错误响应，而不是抛出异常喵。
            return Map.of("ok", false, "error", "list_worlds_failed: " + e.getMessage());
        }
    }

    /**
     * POST /api/worlds
     */
    public static Map<String, Object> handleCreateWorld(ObjectMapper objectMapper, Map<String, Object> req) {
        int worldRadius = 12;
        Object radius = req.get("worldRadius");
        if (radius instanceof Number) {
            worldRadius = ((Number) radius).intValue();
        } else if (radius != null) {
            worldRadius = Integer.parseInt(String.valueOf(radius));
        }
        if (worldRadius < 1) {
            throw new IllegalArgumentException("worldRadius_invalid");
        }

        String worldSeed = req.get("worldSeed") == null ? null : String.valueOf(req.get("worldSeed"));
        String worldName = req.get("worldName") == null ? null : String.valueOf(req.get("worldName")).trim();
        String spawnMode = req.get("spawnMode") == null ? "manual" : String.valueOf(req.get("spawnMode")).trim();
        if (!"manual".equals(spawnMode) && !"random".equals(spawnMode)) {
            spawnMode = "manual";
        }
        String tickPolicy = req.get("tickPolicy") == null ? "RUN_WHEN_ONLINE"
                : String.valueOf(req.get("tickPolicy")).trim();
        if (!"ALWAYS_RUN".equals(tickPolicy) && !"RUN_WHEN_ONLINE".equals(tickPolicy)) {
            tickPolicy = "RUN_WHEN_ONLINE";
        }

        WorldGenConfig cfg = new WorldGenConfig();
        cfg.systemCount = worldRadius;
        cfg.worldSeed = worldSeed;
        cfg.worldType = staraxis.game.world.WorldType.SINGLE_PLAYER;

        StarAxisGameRuntime runtime = StarAxisGameRuntime.newGame(cfg);
        runtime.start();

        String worldId = GameSessions.setRuntime(runtime);
        String finalWorldName = (worldName == null || worldName.isBlank()) ? ("World " + worldId.substring(0, 8)) : worldName;
        GameSessions.registerRuntime(worldId, runtime, finalWorldName, tickPolicy);

        // 创建世界存档目录：gamedata/saves/{worldId}/world.json 喵
        try {
            WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
            Map<String, Object> worldMeta = new LinkedHashMap<>();
            worldMeta.put("worldId", worldId);
            worldMeta.put("worldName", finalWorldName);
            worldMeta.put("tickPolicy", tickPolicy);
            worldMeta.put("createdAtEpochMs", System.currentTimeMillis());
            worldMeta.put("worldRadius", worldRadius);
            worldMeta.put("worldSeed", worldSeed == null ? "" : worldSeed);
            worldMeta.put("spawnMode", spawnMode);
            repo.saveWorldMeta(worldId, worldMeta);
        } catch (Exception e) {
            throw new IllegalArgumentException("world_save_create_failed: " + e.getMessage());
        }

        return Map.of(
                "ok", true,
                "worldId", worldId,
                "worldName", finalWorldName,
                "tickPolicy", tickPolicy,
                "worldRadius", worldRadius);
    }

    /**
     * POST /api/worlds/{worldId}/join
     */
    public static Map<String, Object> handleJoinWorld(ObjectMapper objectMapper, String worldId, Map<String, Object> req, boolean isAdmin) {
        String playerId = req.get("playerId") == null ? null : String.valueOf(req.get("playerId")).trim();
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId_required");
        }

        // 普通用户只能加入“当前运行中的世界”；管理员可指定切换喵。
        String activeWorldId = GameSessions.getActiveWorldId();
        if (!isAdmin) {
            if (activeWorldId == null || activeWorldId.isBlank() || !activeWorldId.equals(worldId)) {
                return Map.of("ok", false, "error", "world_not_running");
            }
        }

        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            runtime = tryLoadWorldRuntimeFromSave(objectMapper, worldId);
        }
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        GameSessions.setActiveWorld(worldId);

        // 先查询玩家是否已有世界角色记录；已有则直接沿用状态，避免重复出生喵。
        try {
            WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
            Map<String, Object> playerRecord = repo.getPlayer(worldId, playerId);
            if (playerRecord != null) {
                String state = playerRecord.get("playerState") == null ? "SPAWN_PENDING"
                        : String.valueOf(playerRecord.get("playerState"));
                String nationId = playerRecord.get("nationId") == null ? null : String.valueOf(playerRecord.get("nationId"));
                if ("SPAWNED".equals(state)) {
                    GameSessions.markPlayerSpawned(worldId, playerId);
                } else {
                    GameSessions.markPlayerJoined(worldId, playerId);
                }
                return Map.of(
                        "ok", true,
                        "worldId", worldId,
                        "playerState", GameSessions.getPlayerState(worldId, playerId),
                        "nationId", nationId == null ? "" : nationId,
                        "playerRole", playerRecord);
            }

            // 首次进入：根据世界 spawnMode 执行出生策略喵。
            Map<String, Object> worldMeta = repo.loadWorldMeta(worldId);
            String spawnMode = worldMeta == null || worldMeta.get("spawnMode") == null
                    ? "manual"
                    : String.valueOf(worldMeta.get("spawnMode"));

            if ("random".equals(spawnMode)) {
                Map<String, Object> spawnReq = new LinkedHashMap<>();
                spawnReq.put("worldId", worldId);
                spawnReq.put("playerId", playerId);
                spawnReq.put("randomSpawn", true);
                Map<String, Object> spawned = JoinGameApi.handleConfirmSpawn(objectMapper, spawnReq);
                boolean ok = Boolean.TRUE.equals(spawned.get("ok"));
                if (!ok) {
                    throw new IllegalArgumentException(String.valueOf(spawned.get("error")));
                }
                String nationId = spawned.get("nationId") == null ? null : String.valueOf(spawned.get("nationId"));
                repo.upsertPlayer(worldId, playerId, null, "SPAWNED", nationId);
                GameSessions.markPlayerSpawned(worldId, playerId);
            } else {
                repo.upsertPlayer(worldId, playerId, null, "SPAWN_PENDING", null);
                GameSessions.markPlayerJoined(worldId, playerId);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("player_registry_update_failed: " + e.getMessage());
        }

        String nationId = "";
        try {
            WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
            Map<String, Object> playerRecord = repo.getPlayer(worldId, playerId);
            if (playerRecord != null && playerRecord.get("nationId") != null) {
                nationId = String.valueOf(playerRecord.get("nationId"));
            }
        } catch (Exception ignored) {
        }

        return Map.of(
                "ok", true,
                "worldId", worldId,
                "playerState", GameSessions.getPlayerState(worldId, playerId),
                "nationId", nationId);
    }

    /**
     * GET /api/worlds/{worldId}/player-state?playerId=...
     */
    public static Map<String, Object> handlePlayerState(ObjectMapper objectMapper, String worldId, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId_required");
        }
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            runtime = tryLoadWorldRuntimeFromSave(objectMapper, worldId);
        }
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        Map<String, Object> playerRecord = null;
        try {
            WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
            playerRecord = repo.getPlayer(worldId, playerId);
        } catch (Exception e) {
            throw new IllegalArgumentException("player_registry_read_failed: " + e.getMessage());
        }

        String playerState = playerRecord == null || playerRecord.get("playerState") == null
                ? GameSessions.getPlayerState(worldId, playerId)
                : String.valueOf(playerRecord.get("playerState"));

        return Map.of(
                "ok", true,
                "worldId", worldId,
                "playerId", playerId,
                "playerState", playerState,
                "playerRole", playerRecord == null ? Map.of() : playerRecord);
    }

    /**
     * 查询世界存档文件列表（latest/autosave/manual）喵。
     */
    public static Map<String, Object> handleListSaves(ObjectMapper objectMapper, String worldId) {
        try {
            WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
            List<Map<String, Object>> saves = repo.listStateSaves(worldId);
            return Map.of("ok", true, "worldId", worldId, "saves", saves);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "list_saves_failed: " + e.getMessage());
        }
    }

    /**
     * 手动存档当前世界到 state.json 与 manual 文件喵（由 game 模块权威服务执行）。
     */
    public static Map<String, Object> handleManualSave(ObjectMapper objectMapper, String worldId, Map<String, Object> req) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            runtime = tryLoadWorldRuntimeFromSave(objectMapper, worldId);
        }
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_not_found");
        }

        String saveId = req.get("saveId") == null ? null : String.valueOf(req.get("saveId")).trim();

        try {
            String finalSaveId = staraxis.game.save.WorldSaveService.saveManual(runtime, worldId, saveId);
            return Map.of("ok", true, "worldId", worldId, "saveId", finalSaveId);
        } catch (Exception e) {
            return Map.of("ok", false, "error", "manual_save_failed: " + e.getMessage());
        }
    }

    /**
     * 自动存档当前世界到 state.json 与 autosave_xxx 文件喵（由 game 模块权威服务执行）。
     */
    public static boolean tryAutoSave(ObjectMapper objectMapper, String worldId) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime == null) {
            return false;
        }

        try {
            staraxis.game.save.WorldSaveService.saveAuto(runtime, worldId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * DELETE /api/worlds/{worldId}
     * 删除世界及其所有存档喵。
     */
    public static Map<String, Object> handleDeleteWorld(ObjectMapper objectMapper, String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return Map.of("ok", false, "error", "worldId_required");
        }

        // 如果世界正在运行，先停止它喵
        StarAxisGameRuntime runtime = GameSessions.getRuntime(worldId);
        if (runtime != null) {
            try {
                runtime.stop();
            } catch (Exception e) {
                // 停止失败继续尝试删除喵
            }
            GameSessions.unregisterRuntime(worldId);
        }

        // 删除世界存档目录喵
        WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
        boolean deleted = repo.deleteWorld(worldId);

        if (deleted) {
            return Map.of("ok", true, "worldId", worldId, "message", "world_deleted");
        } else {
            return Map.of("ok", false, "error", "delete_world_failed");
        }
    }

    /**
     * 从指定存档文件加载世界运行时喵。
     *
     * loadType 取值：latest/auto/manual 喵。
     */
    public static Map<String, Object> handleLoadWorld(ObjectMapper objectMapper, String worldId, Map<String, Object> req) {
        String loadType = req.get("loadType") == null ? "latest" : String.valueOf(req.get("loadType")).trim();
        String fileName = req.get("fileName") == null ? null : String.valueOf(req.get("fileName")).trim();

        StarAxisGameRuntime runtime = tryLoadWorldRuntimeFromSave(objectMapper, worldId, loadType, fileName);
        if (runtime == null) {
            return Map.of("ok", false, "error", "world_load_failed");
        }

        GameSessions.setActiveWorld(worldId);
        return Map.of("ok", true, "worldId", worldId, "loadType", loadType, "fileName", fileName == null ? "state.json" : fileName);
    }

    /**
     * 从 world.json 延迟加载 world runtime（仅最小参数）喵。
     */
    private static StarAxisGameRuntime tryLoadWorldRuntimeFromSave(ObjectMapper objectMapper, String worldId) {
        return tryLoadWorldRuntimeFromSave(objectMapper, worldId, "latest", null);
    }

    /**
     * 从 world.json + state/autosave/manual 恢复 runtime（当前恢复时间轴与世界参数）喵。
     */
    private static StarAxisGameRuntime tryLoadWorldRuntimeFromSave(ObjectMapper objectMapper, String worldId, String loadType, String fileName) {
        if (worldId == null || worldId.isBlank()) {
            return null;
        }

        try {
            WorldSaveRepository repo = new WorldSaveRepository(objectMapper);
            Map<String, Object> meta = repo.loadWorldMeta(worldId);
            if (meta == null || meta.isEmpty()) {
                return null;
            }

            // 读取指定 state 文件（latest/auto/manual）喵
            var statePath = repo.resolveStatePath(worldId, loadType, fileName);
            Map<String, Object> state = repo.loadStateFile(statePath);

            WorldGenConfig cfg = new WorldGenConfig();
            Object radius = meta.get("worldRadius");
            if (state != null) {
                Object worldObj = state.get("world");
                if (worldObj instanceof Map<?, ?> worldMapObj) {
                    Object rr = worldMapObj.get("worldRadius");
                    if (rr != null) {
                        radius = rr;
                    }
                }
            }

            if (radius instanceof Number n) {
                cfg.systemCount = n.intValue();
            } else if (radius != null) {
                cfg.systemCount = Integer.parseInt(String.valueOf(radius));
            } else {
                cfg.systemCount = 500;
            }
            Object seed = meta.get("worldSeed");
            cfg.worldSeed = seed == null ? null : String.valueOf(seed);
            cfg.worldType = staraxis.game.world.WorldType.SINGLE_PLAYER;

            StarAxisGameRuntime runtime = StarAxisGameRuntime.newGame(cfg);
            runtime.start();

            // 使用 LoadWorldCommand 恢复状态（不再直接读写 WorldState）喵。
            if (state != null) {
                Map<String, Object> worldData = null;
                List<Map<String, Object>> nationsList = new ArrayList<>();
                List<Map<String, Object>> entitiesList = new ArrayList<>();
                long nextEntityId = 1L;

                Object worldObj = state.get("world");
                if (worldObj instanceof Map<?, ?> w) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> wMap = (Map<String, Object>) w;
                    worldData = wMap;
                }
                Object nationsObj = state.get("nations");
                if (nationsObj instanceof List<?> rawList) {
                    for (Object item : rawList) {
                        if (item instanceof Map<?, ?> m) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> nm = (Map<String, Object>) m;
                            nationsList.add(nm);
                        }
                    }
                }
                Object entitiesObj = state.get("entities");
                if (entitiesObj instanceof List<?> rawList) {
                    for (Object item : rawList) {
                        if (item instanceof Map<?, ?> m) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> em = (Map<String, Object>) m;
                            entitiesList.add(em);
                        }
                    }
                }
                Object nextEntityIdObj = state.get("nextEntityId");
                if (nextEntityIdObj instanceof Number n) {
                    nextEntityId = n.longValue();
                }

                runtime.executeCommandImmediately(
                        new LoadWorldCommand(worldData, nationsList, entitiesList, nextEntityId));
            }

            String worldName = meta.get("worldName") == null ? worldId : String.valueOf(meta.get("worldName"));
            String tickPolicy = meta.get("tickPolicy") == null ? "RUN_WHEN_ONLINE" : String.valueOf(meta.get("tickPolicy"));
            GameSessions.registerRuntime(worldId, runtime, worldName, tickPolicy);
            return runtime;
        } catch (Exception e) {
            return null;
        }
    }
}