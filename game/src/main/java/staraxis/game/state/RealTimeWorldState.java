package staraxis.game.state;

import staraxis.game.entity.Entity;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.*;

/**
 * RealTimeWorldState（实时世界状态）
 *
 * 实时世界状态（只读快照）：用于战斗、移动、即时事件等实时系统；以及需要即时数据的 UI 展示。
 *
 * 更新方式：每个 simulationTick 结束时，模拟层在 inactive 缓冲中全量填充后 swap 发布为 active。
 *
 * 核心数据结构：以扁平化的实体表（entitiesById）为核心，并提供按恒星系的索引。
 * 3D 版本：已移除 hex 时代的所有 SectorCoord 相关数据。
 */
public class RealTimeWorldState {

    public long simulationTick;

    public long totalGameSeconds;
    public double totalGameSecondsExact;
    public double deltaGameSeconds;
    public int worldRadius;
    public staraxis.game.world.WorldType worldType;
    public double gameSecondsPerRealSecond;
    public double timeScale;
    public int year, month, day, hour, minute, second;

    /** 实体总表（entityId -> Entity）。 */
    private final Map<Long, Entity> entitiesById = new LinkedHashMap<>();

    /** 恒星系实体索引（systemId -> entityId列表）。 */
    private final Map<Long, List<Long>> entityIdsBySystem = new LinkedHashMap<>();

    /** 恒星系世界坐标索引（systemId -> 3D坐标）。 */
    private final Map<Long, SpacePosition> systemPositions = new LinkedHashMap<>();

    private final List<EntitySnapshot> entitySnapshots = new ArrayList<>();

    /** 按恒星系组织的实体快照索引（systemId字符串 -> 快照列表）。 */
    private final Map<String, List<EntitySnapshot>> entitySnapshotsBySystem = new LinkedHashMap<>();

    public RealTimeWorldState() {
    }

    public void resetForFill() {
        simulationTick = 0;
        totalGameSeconds = 0;
        totalGameSecondsExact = 0;
        deltaGameSeconds = 0;
        worldRadius = 0;
        worldType = null;
        gameSecondsPerRealSecond = 0;
        timeScale = 0;
        year = month = day = hour = minute = second = 0;
        entitiesById.clear();
        entityIdsBySystem.clear();
        systemPositions.clear();
        entitySnapshots.clear();
        entitySnapshotsBySystem.clear();
    }

    public void putEntity(Entity entity) {
        if (entity == null) return;
        entitiesById.put(entity.entityId, entity);
    }

    public void putEntitySnapshot(EntitySnapshot snapshot) {
        if (snapshot == null) return;
        entitySnapshots.add(snapshot);

        // 按 systemId 索引
        if (snapshot.systemId > 0) {
            String key = String.valueOf(snapshot.systemId);
            entitySnapshotsBySystem.computeIfAbsent(key, k -> new ArrayList<>()).add(snapshot);
        }
    }

    public void putEntitySystem(long systemId, long entityId) {
        entityIdsBySystem.computeIfAbsent(systemId, k -> new ArrayList<>()).add(entityId);
    }

    public void putSystemPosition(long systemId, SpacePosition position) {
        systemPositions.put(systemId, position);
    }

    // --- 只读视图 ---

    public Map<Long, Entity> getEntitiesByIdView() {
        return Collections.unmodifiableMap(entitiesById);
    }

    public Map<Long, List<Long>> getEntityIdsBySystemView() {
        return Collections.unmodifiableMap(entityIdsBySystem);
    }

    public Map<Long, SpacePosition> getSystemPositionsView() {
        return Collections.unmodifiableMap(systemPositions);
    }

    public List<EntitySnapshot> getEntitySnapshotsView() {
        return Collections.unmodifiableList(entitySnapshots);
    }

    public Map<String, List<EntitySnapshot>> getEntitySnapshotsBySystemView() {
        return Collections.unmodifiableMap(entitySnapshotsBySystem);
    }

    public void sortEntitySnapshotsByIntelLevel() {
        for (List<EntitySnapshot> snapshots : entitySnapshotsBySystem.values()) {
            if (snapshots.size() > 1) {
                snapshots.sort(java.util.Comparator.comparingInt(es -> es.intelRequiredLevel));
            }
        }
    }
}
