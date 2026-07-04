package staraxis.game.state;

import staraxis.game.entity.Entity;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

import java.util.*;

/**
 * RealTimeWorldState（实时世界状态）
 *
 * 实时世界状态（只读快照）：用于战斗、移动、即时事件等实时系统；以及需要即时数据的 UI 展示。
 *
 * 更新方式：每个 simulationTick 结束时，模拟层在 inactive 缓冲中全量填充后 swap 发布为 active。
 *
 * 核心数据结构：以扁平化的实体表（entitiesById）为核心，并提供按星区/星系的索引，以支持“归属可变”与高效查询。
 */
public class RealTimeWorldState {

    public long simulationTick;

    /** 权威累计游戏秒（向下取整）喵。 */
    public long totalGameSeconds;
    public double totalGameSecondsExact;

    /** 本 tick 推进的游戏秒数（Δt）喵。 */
    public double deltaGameSeconds;

    public int worldRadius;

    /** 世界类型：用于前端 HUD 与权限展示喵。 */
    public staraxis.game.world.WorldType worldType;

    /** 现实 1 秒推进的游戏秒数（不含 timeScale）喵。 */
    public double gameSecondsPerRealSecond;

    /** 系统时间倍率喵。 */
    public double timeScale;

    // 结构化游戏日期时间字段喵
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int second;

    /** 实体总表（entityId -> Entity），新的核心数据结构。 */
    private final Map<Long, Entity> entitiesById = new LinkedHashMap<>();

    /** 空间索引（sectorCoord -> entityIds），用于按星区查询。 */
    private final Map<SectorCoord, List<Long>> entityIdsBySector = new LinkedHashMap<>();

    /** 系统索引（systemId -> entityIds），用于按恒星系查询。 */
    private final Map<Long, List<Long>> entityIdsBySystem = new LinkedHashMap<>();

    /** 恒星系世界坐标索引（systemId -> 在银河中的3D坐标）。 */
    private final Map<Long, SpacePosition> systemPositions = new LinkedHashMap<>();

    /** 星区中心点缓存（sectorCoord -> centerWorldGU）。 */
    private final Map<SectorCoord, Vec2d> sectorCentersWorldGU = new LinkedHashMap<>();

    /** 星区归属缓存（"q,r" -> ownerNationId）。 */
    private final Map<String, String> sectorOwnerNationIdByCoord = new LinkedHashMap<>();

    private final List<EntitySnapshot> entitySnapshots = new ArrayList<>();

    /** 按星区组织的实体快照索引（sectorCoord -> 快照列表）喵。 */
    private final Map<SectorCoord, List<EntitySnapshot>> entitySnapshotsBySector = new LinkedHashMap<>();

    public RealTimeWorldState() {
    }

    /**
     * 全量填充前调用：清空并准备写入。
     */
    public void resetForFill() {
        simulationTick = 0;
        totalGameSeconds = 0;
        totalGameSecondsExact = 0;
        deltaGameSeconds = 0;
        worldRadius = 0;
        worldType = null;
        gameSecondsPerRealSecond = 0;
        timeScale = 0;
        year = 0;
        month = 0;
        day = 0;
        hour = 0;
        minute = 0;
        second = 0;
        entitiesById.clear();
        entityIdsBySector.clear();
        entityIdsBySystem.clear();
        systemPositions.clear();
        sectorCentersWorldGU.clear();
        sectorOwnerNationIdByCoord.clear();
        entitySnapshots.clear();
        entitySnapshotsBySector.clear();
    }

    /**
     * 模拟层填充：注册一个实体及其索引。
     */
    public void putEntity(Entity entity) {
        if (entity == null)
            return;

        entitiesById.put(entity.entityId, entity);
    }

    public void putEntitySnapshot(EntitySnapshot snapshot) {
        if (snapshot == null)
            return;

        entitySnapshots.add(snapshot);

        // 同时按星区索引喵
        if (snapshot.sectorCoord != null) {
            entitySnapshotsBySector
                .computeIfAbsent(snapshot.sectorCoord, k -> new ArrayList<>())
                .add(snapshot);
        }
    }

    /**
     * 模拟层填充：注册实体到其所属的恒星系索引。
     */
    public void putEntitySystem(long systemId, long entityId) {
        entityIdsBySystem.computeIfAbsent(systemId, k -> new ArrayList<>()).add(entityId);
    }

    /**
     * 模拟层填充：写入恒星系世界坐标。
     */
    public void putSystemPosition(long systemId, SpacePosition position) {
        systemPositions.put(systemId, position);
    }

    /**
     * 模拟层填充：写入一个星区中心点。
     */
    public void putSectorCenter(SectorCoord coord, Vec2d centerWorldGU) {
        sectorCentersWorldGU.put(coord, centerWorldGU);
    }

    /**
     * 模拟层填充：写入一个星区归属。
     */
    public void putSectorOwnerNationId(SectorCoord coord, String ownerNationId) {
        if (coord == null) {
            return;
        }
        sectorOwnerNationIdByCoord.put(coord.q() + "," + coord.r(), ownerNationId);
    }

    // --- 只读视图 --- //

    public Map<Long, Entity> getEntitiesByIdView() {
        return Collections.unmodifiableMap(entitiesById);
    }

    public Map<SectorCoord, List<Long>> getEntityIdsBySectorView() {
        return Collections.unmodifiableMap(entityIdsBySector);
    }

    public Map<Long, List<Long>> getEntityIdsBySystemView() {
        return Collections.unmodifiableMap(entityIdsBySystem);
    }

    public Map<Long, SpacePosition> getSystemPositionsView() {
        return Collections.unmodifiableMap(systemPositions);
    }

    public Map<SectorCoord, Vec2d> getSectorCentersWorldGUView() {
        return Collections.unmodifiableMap(sectorCentersWorldGU);
    }

    public Map<String, String> getSectorOwnerNationIdByCoordView() {
        return Collections.unmodifiableMap(sectorOwnerNationIdByCoord);
    }

    public List<EntitySnapshot> getEntitySnapshotsView() {
        return Collections.unmodifiableList(entitySnapshots);
    }

    /**
     * 获取按星区组织的实体快照只读视图喵。
     *
     * @return 不可修改的 Map，key 为星区坐标，value 为该星区的实体快照列表喵
     */
    public Map<SectorCoord, List<EntitySnapshot>> getEntitySnapshotsBySectorView() {
        // 返回不可修改的视图，但内部列表仍然是可变的（由填充层控制）喵
        return Collections.unmodifiableMap(entitySnapshotsBySector);
    }

    /**
     * 对所有星区的实体快照按情报等级排序喵。
     *
     * 说明：
     * - 用于 Webnet 二分查找快速裁剪可见实体喵。
     * - 排序规则：按 intelRequiredLevel 升序（0级在前，高等级在后）喵。
     * - 应在 publishRealTimeSnapshot() 填充完所有快照后调用喵。
     */
    public void sortEntitySnapshotsByIntelLevel() {
        for (List<EntitySnapshot> sectorSnapshots : entitySnapshotsBySector.values()) {
            if (sectorSnapshots.size() > 1) {
                sectorSnapshots.sort(java.util.Comparator.comparingInt(es -> es.intelRequiredLevel));
            }
        }
    }
}
