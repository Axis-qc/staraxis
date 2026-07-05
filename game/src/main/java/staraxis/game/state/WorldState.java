package staraxis.game.state;

import staraxis.game.astro.AstroData;
import staraxis.game.entity.Entity;
import staraxis.game.sim.SimulationTime;
import staraxis.game.nation.NationManager;
import staraxis.game.nation.NationSpawnService;
import staraxis.game.player.PlayerManager;
import staraxis.game.space.SpacePosition;
import staraxis.game.nation.VisibilitySystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import staraxis.game.space.event.CrossSystemEventTable;
import staraxis.game.space.octree.GalaxyOctree;
import staraxis.game.sim.TickDispatcher;

/**
 * WorldState
 *
 * 游戏运行时的唯一权威世界状态容器（只允许模拟层读写）。
 */
public class WorldState {

    public final SimulationTime time;

    /** 世界半径（GU），用于前端 HUD 展示。 */
    public int worldRadius;

    /** 权威星体数据（恒星系、恒星、行星等）：仅允许模拟层读写。 */
    public final AstroData astro;

    /**
     * 上一次低频基线快照发布时的游戏总秒数。
     */
    public long lastBaselinePublishGameSeconds = 0;

    /**
     * 低频快照是否因数据变化而变脏（需要尽快推送）。
     */
    public boolean baselineDirty = false;

    /**
     * 实时状态修订号：只有真正影响前端同步的数据变化时才递增喵。
     */
    private long realtimeStateRevision = 1L;

    /**
     * 当前世界级并集关注实体集合喵。
     *
     * 语义喵：
     * - 由 webnet 汇总所有快照订阅连接上报的“视野内实体”并集喵。
     * - 被包含的实体应走逐 Tick 完整计算喵，避免玩家眼前的运动被简化模式打散喵。
     */
    private volatile Set<Long> fullRealtimeSimulationEntityIds = Set.of();

    /**
     * 最近一次已经写入实时快照缓冲的修订号喵。
     */
    private long publishedRealtimeStateRevision = 0L;

    /**
     * 实体总表（entityId -> Entity）。
     */
    public final Map<Long, Entity> entitiesById = new HashMap<>();

    /** 恒星系实体索引（systemId -> entityId列表），由 registerEntity 自动维护。 */
    public final Map<Long, List<Long>> entityIdsBySystem = new HashMap<>();

    /** 恒星系世界坐标索引（systemId -> 在银河中的3D坐标）。 */
    public final Map<Long, SpacePosition> systemPositions = new HashMap<>();

    /** 国家管理器：管理所有国家的运行时状态、玩家归属和外交关系。 */
    public final NationManager nationManager = new NationManager();

    /** 玩家管理器：管理所有玩家的运行时状态。与 NationManager 平行。 */
    public final PlayerManager playerManager = new PlayerManager();

    /**
     * 资产管理器：统一管理 Player 和 Nation 两个维度的实体所有权。
     */
    public final staraxis.game.nation.AssetManager assetManager = new staraxis.game.nation.AssetManager(
            this);

    /**
     * 国家出生点服务：根据出生策略为国家选择并分配初始星系与首都星球。
     */
    public final NationSpawnService nationSpawnService = new NationSpawnService(this);

    /**
     * 可见性系统：计算每个国家的可见性状态。
     */
    public final VisibilitySystem visibilitySystem = new VisibilitySystem(this);

    /**
     * 情报系统：计算探测等级与情报可见性（数据驱动）。
     *
     * 说明：
     * - 由 StarAxisGameRuntime.newGame 初始化后注入喵。
     */
    public staraxis.game.intel.IntelSystem intelSystem;
    /** 跨系统事件表（在途实体索引 + 按 tick 到达到期事件）。 */
    public final CrossSystemEventTable crossSystemEventTable = new CrossSystemEventTable();

    /** 星系八叉树空间索引（只读，每 tick 重建，用于恒星拾取/传感器范围查询）。 */
    public final GalaxyOctree galaxyOctree = new GalaxyOctree();

    /** Tick 分派器（管理 5 阶段流水线调度 + LPT 分配）。 */
    public final TickDispatcher tickDispatcher = new TickDispatcher();
    /**
     * 全局实体 ID 生成器（nextEntityId）喵。
     *
     * 作用：
     * - 确保所有动态实体（SHIP、STATION 等）拥有全局唯一且递增的 ID喵。
     * - 起始值设为 1000000L，避免与天文实体（STAR、PLANET）ID 冲突喵。
     *
     * 存档一致性：
     * - 存档时必须序列化此字段，加载时恢复，保证重启后 ID 不重复喵。
     */
    private long nextEntityId = 1000000L;

    public WorldState(SimulationTime time, int worldRadius, AstroData astro) {
        this.time = time;
        this.worldRadius = worldRadius;
        this.astro = astro;
    }

    public void registerEntity(Entity entity) {
        if (entity == null) return;
        entitiesById.put(entity.entityId, entity);
        if (entity.systemId > 0) {
            entityIdsBySystem.computeIfAbsent(entity.systemId, k -> new ArrayList<>()).add(entity.entityId);
        }
    }

    /**
     * 生成全局唯一实体 ID（generateEntityId）喵。
     *
     * 作用：
     * - 线程安全递增 nextEntityId，确保多线程环境下 ID 不重复喵。
     * - 返回递增后的 ID 值，用于新实体创建喵。
     *
     * 使用场景：
     * - 舰船生成、空间站生成等动态实体创建时调用喵。
     * - 禁止直接修改 nextEntityId 字段，必须通过此方法获取喵。
     *
     * @return 下一个可用的实体 ID（>0）
     */
    public synchronized long generateEntityId() {
        return nextEntityId++;
    }

    /**
     * 获取下一个实体 ID（getNextEntityId）喵。
     *
     * 作用：
     * - 用于存档序列化，保存当前 ID 生成器状态喵。
     * - 仅限存档服务使用，业务逻辑请使用 generateEntityId() 喵。
     *
     * @return 下一个可用的实体 ID（未递增）
     */
    public synchronized long getNextEntityId() {
        return nextEntityId;
    }

    /**
     * 设置下一个实体 ID（setNextEntityId）喵。
     *
     * 作用：
     * - 用于存档反序列化，恢复 ID 生成器状态喵。
     * - 仅限存档加载时调用，确保新生成的实体 ID 不与现有实体冲突喵。
     * - 若传入值小于当前 nextEntityId，则忽略（安全保护）喵。
     *
     * @param value 要设置的下一实体 ID（必须 >0）
     */
    public synchronized void setNextEntityId(long value) {
        if (value > nextEntityId) {
            nextEntityId = value;
        }
    }

    public synchronized long markRealtimeDirty() {
        return ++realtimeStateRevision;
    }

    public synchronized long getRealtimeStateRevision() {
        return realtimeStateRevision;
    }

    public synchronized long getPublishedRealtimeStateRevision() {
        return publishedRealtimeStateRevision;
    }

    public synchronized boolean hasUnpublishedRealtimeChanges() {
        return realtimeStateRevision != publishedRealtimeStateRevision;
    }

    public synchronized void markRealtimeRevisionPublished() {
        publishedRealtimeStateRevision = realtimeStateRevision;
    }

    /**
     * 替换当前世界级并集关注实体集合喵。
     */
    public void replaceFullRealtimeSimulationEntityIds(Set<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            fullRealtimeSimulationEntityIds = Set.of();
            return;
        }
        fullRealtimeSimulationEntityIds = Set.copyOf(entityIds);
    }

    /**
     * 判断指定实体是否必须走逐 Tick 完整计算喵。
     */
    public boolean shouldUseFullRealtimeSimulation(long entityId) {
        return fullRealtimeSimulationEntityIds.contains(entityId);
    }
}
