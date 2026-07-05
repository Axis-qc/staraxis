package staraxis.game.nation;

import staraxis.game.state.WorldState;
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
 *
 * 可见性由 SnapshotBroadcaster 每 tick 通过 computeIntelVisibleSystems3D() 按需计算，
 * 不再预存到 NationState 集合中。
 */
public class VisibilitySystem {

    private final WorldState worldState;

    public VisibilitySystem(WorldState worldState) {
        this.worldState = worldState;
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
