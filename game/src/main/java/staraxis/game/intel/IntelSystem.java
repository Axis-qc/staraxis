package staraxis.game.intel;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.intel.def.IntelConfigDef;
import staraxis.game.state.WorldState;
import staraxis.game.space.SpacePosition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IntelSystem（情报系统）
 *
 * 3D 版本：使用 Octree 空间查询替代旧的 hex 网格扩展。
 * 探测等级由拥有者实体发出，按距离衰减；目标距离越近等级越高。
 */
public class IntelSystem {

    private final WorldState worldState;
    private final IntelConfigDef config;

    public IntelSystem(WorldState worldState, IntelConfigDef config) {
        this.worldState = worldState;
        this.config = config != null ? config : new IntelConfigDef();
    }

    public IntelConfigDef getConfig() {
        return config;
    }

    /**
     * 获取指定实体类型的情报需求等级。
     */
    public int getRequiredIntelLevel(EntityType type) {
        if (type == null) return 10;
        return config.intelRequiredLevelByEntityType.getOrDefault(type, 4);
    }

    /**
     * 判断指定国家是否能看到指定实体的细节（3D Octree 版本）。
     */
    public boolean canSeeEntity3D(String nationId, Entity entity) {
        if (entity == null) return false;

        int required = getRequiredIntelLevel(entity.entityType);
        if (required <= 0) return true;
        if (nationId == null || nationId.isBlank()) return false;
        if (nationId.equals(entity.ownerNationId)) return true;
        if (entity.posWorldGU == null) return false;

        return getEffectiveDetectorLevel3D(nationId, entity.posWorldGU) >= required;
    }

    /**
     * 获取指定国家在指定位置的有效探测等级（Octree 球体查询）。
     * 遍历本国所有探测源，计算 3D 距离 + 线性衰减。
     */
    public int getEffectiveDetectorLevel3D(String nationId, SpacePosition targetPos) {
        if (nationId == null || targetPos == null) return -1;

        int maxLevel = -1;

        for (Entity e : worldState.entitiesById.values()) {
            if (e == null || !nationId.equals(e.ownerNationId) || e.posWorldGU == null) continue;

            Integer strength = config.detectorSourceStrengthByEntityType.get(e.entityType);
            Integer range = config.detectorSourceRangeByEntityType.get(e.entityType);
            if (strength == null || range == null) continue;

            double rangeGU = range * 50000.0;
            double dist = targetPos.distanceTo(e.posWorldGU);

            if (dist <= rangeGU) {
                double falloff = 1.0 - (dist / rangeGU);
                int level = (int) Math.round(strength * (0.5 + 0.5 * falloff));
                level = Math.max(0, Math.min(10, level));
                maxLevel = Math.max(maxLevel, level);
            }
        }

        return maxLevel;
    }

    /**
     * 获取指定国家在指定实体位置的有效探测等级。
     */
    public int getEffectiveDetectorLevel3D(String nationId, Entity target) {
        if (target == null || target.posWorldGU == null) return -1;
        return getEffectiveDetectorLevel3D(nationId, target.posWorldGU);
    }

    /**
     * 使用 GalaxyOctree 球体查询获取指定国家可见的所有实体（含衰减计算）。
     */
    public Set<Long> getVisibleEntities3D(String nationId, int minLevel) {
        Set<Long> result = new HashSet<>();
        if (nationId == null) return result;

        for (Entity e : worldState.entitiesById.values()) {
            if (e == null || !nationId.equals(e.ownerNationId) || e.posWorldGU == null) continue;

            Integer strength = config.detectorSourceStrengthByEntityType.get(e.entityType);
            Integer range = config.detectorSourceRangeByEntityType.get(e.entityType);
            if (strength == null || range == null) continue;

            double rangeGU = range * 50000.0;
            List<Long> nearby = worldState.galaxyOctree.querySphere(e.posWorldGU, rangeGU);

            for (long targetId : nearby) {
                Entity target = worldState.entitiesById.get(targetId);
                if (target == null || target.posWorldGU == null) continue;

                double dist = target.posWorldGU.distanceTo(e.posWorldGU);
                double falloff = 1.0 - (dist / rangeGU);
                int level = (int) Math.round(strength * (0.5 + 0.5 * falloff));
                level = Math.max(0, Math.min(10, level));

                if (level >= minLevel) {
                    result.add(targetId);
                }
            }
        }

        return result;
    }
}
