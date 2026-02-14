package staraxis.game.state;

import staraxis.game.entity.Entity;
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

    public int gameDatetimeDay;

    public double accGameHoursInDay;

    public int worldRadius;

    /** 实体总表（entityId -> Entity），新的核心数据结构。 */
    private final Map<Long, Entity> entitiesById = new LinkedHashMap<>();

    /** 空间索引（sectorCoord -> entityIds），用于按星区查询。 */
    private final Map<SectorCoord, List<Long>> entityIdsBySector = new LinkedHashMap<>();

    /** 系统索引（systemId -> entityIds），用于按恒星系查询。 */
    private final Map<Long, List<Long>> entityIdsBySystem = new LinkedHashMap<>();

    /** 星区中心点缓存（sectorCoord -> centerWorldGU）。 */
    private final Map<SectorCoord, Vec2d> sectorCentersWorldGU = new LinkedHashMap<>();

    /** 星区归属缓存（"q,r" -> ownerNationId）。 */
    private final Map<String, String> sectorOwnerNationIdByCoord = new LinkedHashMap<>();

    private final List<EntitySnapshot> entitySnapshots = new ArrayList<>();

    public RealTimeWorldState() {
    }

    /**
     * 全量填充前调用：清空并准备写入。
     */
    public void resetForFill() {
        simulationTick = 0;
        gameDatetimeDay = 0;
        accGameHoursInDay = 0;
        worldRadius = 0;
        entitiesById.clear();
        entityIdsBySector.clear();
        entityIdsBySystem.clear();
        sectorCentersWorldGU.clear();
        sectorOwnerNationIdByCoord.clear();
        entitySnapshots.clear();
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

    public Map<SectorCoord, Vec2d> getSectorCentersWorldGUView() {
        return Collections.unmodifiableMap(sectorCentersWorldGU);
    }

    public Map<String, String> getSectorOwnerNationIdByCoordView() {
        return Collections.unmodifiableMap(sectorOwnerNationIdByCoord);
    }

    public List<EntitySnapshot> getEntitySnapshotsView() {
        return Collections.unmodifiableList(entitySnapshots);
    }
}
