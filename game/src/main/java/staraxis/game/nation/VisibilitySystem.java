package staraxis.game.nation;

import staraxis.game.state.WorldState;
import staraxis.game.space.SpacePosition;
import staraxis.game.entity.Entity;

import java.util.HashSet;
import java.util.Set;

/**
 * VisibilitySystem（可见性系统）
 *
 * 3D 版本：使用恒星系（systemId）替代 hex 网格进行可见性判定。
 * 可见性由以下因素决定：
 * 1. 本国拥有实体所在的恒星系完全可见
 * 2. 传感器范围内的恒星系部分可见（通过 Octree 球体查询）
 * 3. 同盟国有单位的恒星系共享可见性
 * 4. 特殊实体（恒星等）对所有国家可见
 */
public class VisibilitySystem {

    private final WorldState worldState;

    public VisibilitySystem(WorldState worldState) {
        this.worldState = worldState;
    }

    /**
     * 更新所有国家的可见性状态（每 tick 调用）。
     */
    public void updateAllNationsVisibility() {
        for (String nationId : worldState.nationManager.getAllNationIds()) {
            updateNationVisibility(nationId);
        }
    }

    /**
     * 更新指定国家的可见性状态（每 tick 调用）。
     */
    public void updateNationVisibility(String nationId) {
        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState == null) return;

        // 清除当前可见集合
        nationState.visibleSectorCoords.clear();

        // 计算可见恒星系集合
        Set<String> visibleSystems = computeVisibleSystems(nationId);
        for (String systemKey : visibleSystems) {
            nationState.addVisibleSector(systemKey);
            if (!nationState.isSectorExplored(systemKey)) {
                nationState.addExploredSector(systemKey);
            }
        }
    }

    /**
     * 计算指定国家可见的恒星系（systemId 字符串）。
     * 规则：
     * 1. 本国拥有实体的星系可见
     * 2. 传感器范围内的星系可见
     * 3. 同盟国单位的星系可见
     */
    public Set<String> computeVisibleSystems(String nationId) {
        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState == null || !nationState.isActive()) {
            return new HashSet<>();
        }

        Set<String> visible = new HashSet<>();

        // 1. 遍历本国实体所在的恒星系
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity != null && nationId.equals(entity.ownerNationId) && entity.systemId > 0) {
                visible.add(String.valueOf(entity.systemId));
            }
        }

        // 2. 传感器范围：通过 Octree 球体查询扩展
        double sensorRangeGU = nationState.getSensorRangeGU();
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity == null || !nationId.equals(entity.ownerNationId)) continue;
            if (entity.posWorldGU == null) continue;

            var nearby = worldState.galaxyOctree.querySphere(entity.posWorldGU, sensorRangeGU);
            for (long targetId : nearby) {
                Entity target = worldState.entitiesById.get(targetId);
                if (target != null && target.systemId > 0) {
                    visible.add(String.valueOf(target.systemId));
                }
            }
        }

        // 3. 同盟国共享
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity == null || entity.ownerNationId == null || entity.systemId <= 0) continue;
            if (worldState.nationManager.getNationState(entity.ownerNationId) != null
                    && nationState.isAlliedWith(entity.ownerNationId)) {
                visible.add(String.valueOf(entity.systemId));
            }
        }

        return visible;
    }

    /**
     * 计算指定实体的对指定国家的可见性级别。
     */
    public String computeEntityVisibility(Entity entity, String nationId) {
        if (entity == null) return "NONE";

        // 恒星对所有国家完全可见
        if (entity.entityType == staraxis.game.entity.EntityType.STAR) return "FULL";
        if (nationId.equals(entity.ownerNationId)) return "FULL";

        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState == null) return "NONE";
        if (entity.systemId <= 0) return "NONE";

        String key = String.valueOf(entity.systemId);

        if (nationState.isSectorVisible(key)) return "PARTIAL";
        if (nationState.isSectorExplored(key)) return "PARTIAL";

        return "NONE";
    }

    /**
     * 计算指定国家的情报可见恒星系集合（替代旧 hex 版本）。
     * 由 SnapshotBroadcaster 等调用，用于过滤实体分发。
     */
    public Set<Long> computeIntelVisibleSystems3D(String nationId) {
        Set<Long> result = new HashSet<>();
        if (nationId == null || nationId.isBlank()) return result;

        Set<Long> ownedSystems = new HashSet<>();
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity != null && nationId.equals(entity.ownerNationId) && entity.systemId > 0) {
                ownedSystems.add(entity.systemId);
            }
        }
        result.addAll(ownedSystems);

        // 传感器范围扩展
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity == null || !nationId.equals(entity.ownerNationId) || entity.posWorldGU == null) continue;

            double sensorRangeGU = 100_000.0;
            var nationState = worldState.nationManager.getNationState(nationId);
            if (nationState != null) sensorRangeGU = nationState.getSensorRangeGU();

            var nearby = worldState.galaxyOctree.querySphere(entity.posWorldGU, sensorRangeGU);
            for (long targetId : nearby) {
                Entity target = worldState.entitiesById.get(targetId);
                if (target != null && target.systemId > 0) {
                    result.add(target.systemId);
                }
            }
        }

        return result;
    }
}
