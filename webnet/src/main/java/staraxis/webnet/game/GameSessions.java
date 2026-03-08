package staraxis.webnet.game;

import staraxis.game.StarAxisGameRuntime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GameSessions
 *
 * 多世界运行时存储（webnet 进程内）喵。
 *
 * 约束：
 * - 当前阶段仍是进程内内存会话，不做跨进程持久化喵。
 * - 允许维护多个 worldId -> runtime，并保留一个当前激活世界喵。
 */
public final class GameSessions {

    /** worldId -> runtime（按创建顺序保留）喵。 */
    private static final Map<String, StarAxisGameRuntime> runtimesByWorldId = new LinkedHashMap<>();

    /** worldId -> 世界元信息喵。 */
    private static final Map<String, WorldSessionMeta> metasByWorldId = new LinkedHashMap<>();

    /** worldId -> (playerId -> playerWorldState) 喵。 */
    private static final Map<String, Map<String, String>> playerStatesByWorldId = new LinkedHashMap<>();

    /** 当前激活世界（供旧链路兼容读取）喵。 */
    private static volatile String activeWorldId;

    private GameSessions() {
    }

    /**
     * 兼容旧入口：注册一个 runtime 并自动生成 worldId，同时切为 active 喵。
     */
    public static synchronized String setRuntime(StarAxisGameRuntime runtime) {
        String worldId = "world_" + UUID.randomUUID().toString().replace("-", "");
        registerRuntime(worldId, runtime, "World " + (runtimesByWorldId.size() + 1), "RUN_WHEN_ONLINE");
        return worldId;
    }

    /**
     * 注册或覆盖指定 worldId 的 runtime，并记录元信息喵。
     */
    public static synchronized void registerRuntime(String worldId, StarAxisGameRuntime runtime,
            String worldName, String tickPolicy) {
        if (worldId == null || worldId.isBlank() || runtime == null) {
            return;
        }

        // 单运行世界约束：同一时刻仅保留一个运行时喵。
        runtimesByWorldId.clear();
        runtimesByWorldId.put(worldId, runtime);

        WorldSessionMeta meta = metasByWorldId.get(worldId);
        if (meta == null) {
            meta = new WorldSessionMeta();
            meta.worldId = worldId;
            meta.createdAtEpochMs = System.currentTimeMillis();
        }
        meta.worldName = (worldName == null || worldName.isBlank()) ? worldId : worldName;
        meta.tickPolicy = (tickPolicy == null || tickPolicy.isBlank()) ? "RUN_WHEN_ONLINE" : tickPolicy;
        meta.active = false;
        metasByWorldId.put(worldId, meta);
        playerStatesByWorldId.computeIfAbsent(worldId, k -> new LinkedHashMap<>());

        setActiveWorld(worldId);
    }

    /**
     * 切换当前激活世界喵。
     */
    public static synchronized boolean setActiveWorld(String worldId) {
        if (worldId == null || !runtimesByWorldId.containsKey(worldId)) {
            return false;
        }
        activeWorldId = worldId;
        for (WorldSessionMeta meta : metasByWorldId.values()) {
            meta.active = worldId.equals(meta.worldId);
        }
        return true;
    }

    /**
     * 获取当前激活 runtime（旧链路兼容）喵。
     */
    public static synchronized StarAxisGameRuntime getRuntime() {
        if (activeWorldId == null) {
            return null;
        }
        return runtimesByWorldId.get(activeWorldId);
    }

    /**
     * 获取指定 worldId 的 runtime 喵。
     */
    public static synchronized StarAxisGameRuntime getRuntime(String worldId) {
        if (worldId == null) {
            return null;
        }
        return runtimesByWorldId.get(worldId);
    }

    public static synchronized boolean hasRuntime() {
        return getRuntime() != null;
    }

    public static synchronized List<WorldSessionMeta> listWorlds() {
        return new ArrayList<>(metasByWorldId.values());
    }

    public static synchronized String getActiveWorldId() {
        return activeWorldId;
    }

    /**
     * 获取指定世界推进策略（tickPolicy）喵。
     */
    public static synchronized String getTickPolicy(String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return "RUN_WHEN_ONLINE";
        }
        WorldSessionMeta meta = metasByWorldId.get(worldId);
        if (meta == null || meta.tickPolicy == null || meta.tickPolicy.isBlank()) {
            return "RUN_WHEN_ONLINE";
        }
        return meta.tickPolicy;
    }

    /**
     * 获取当前激活世界推进策略（tickPolicy）喵。
     */
    public static synchronized String getActiveTickPolicy() {
        return getTickPolicy(activeWorldId);
    }

    /**
     * 记录玩家加入世界（未出生）状态喵。
     */
    public static synchronized void markPlayerJoined(String worldId, String playerId) {
        if (worldId == null || worldId.isBlank() || playerId == null || playerId.isBlank()) {
            return;
        }
        playerStatesByWorldId
                .computeIfAbsent(worldId, k -> new LinkedHashMap<>())
                .put(playerId, "SPAWN_PENDING");
    }

    /**
     * 记录玩家已完成出生状态喵。
     */
    public static synchronized void markPlayerSpawned(String worldId, String playerId) {
        if (worldId == null || worldId.isBlank() || playerId == null || playerId.isBlank()) {
            return;
        }
        playerStatesByWorldId
                .computeIfAbsent(worldId, k -> new LinkedHashMap<>())
                .put(playerId, "SPAWNED");
    }

    /**
     * 查询玩家在世界中的状态喵。
     */
    public static synchronized String getPlayerState(String worldId, String playerId) {
        if (worldId == null || worldId.isBlank() || playerId == null || playerId.isBlank()) {
            return "NOT_JOINED";
        }
        Map<String, String> states = playerStatesByWorldId.get(worldId);
        if (states == null) {
            return "NOT_JOINED";
        }
        return states.getOrDefault(playerId, "NOT_JOINED");
    }

    /**
     * 获取世界在线玩家数量（近似：标记为已加入的玩家数）喵。
     */
    public static synchronized int getJoinedPlayerCount(String worldId) {
        if (worldId == null || worldId.isBlank()) {
            return 0;
        }
        Map<String, String> states = playerStatesByWorldId.get(worldId);
        return states == null ? 0 : states.size();
    }

    /**
     * 世界会话元信息喵。
     */
    public static class WorldSessionMeta {
        public String worldId;
        public String worldName;
        public String tickPolicy;
        public long createdAtEpochMs;
        public boolean active;
    }
}
