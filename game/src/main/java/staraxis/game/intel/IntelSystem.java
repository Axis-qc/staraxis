package staraxis.game.intel;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.intel.def.IntelConfigDef;
import staraxis.game.state.WorldState;
import staraxis.game.world.hex.HexMath;
import staraxis.game.world.hex.SectorCoord;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IntelSystem（情报系统）
 *
 * 作用：计算每个国家在不同星区的探测等级，并判定实体的可见性。
 * 核心逻辑：
 * 1. 探测等级完全由实体（探测源）提供，不再有国家全局加成喵。
 * 2. 每个星区的有效探测等级取决于所有覆盖该星区的探测源提供的最高值喵。
 * 3. 结果会按国家进行缓存，仅在数值更新（markDirty）时重算喵。
 */
public class IntelSystem {

    private final WorldState worldState;
    private final IntelConfigDef config;

    /**
     * 探测等级缓存：nationId -> (sectorKey -> effectiveLevel) 喵。
     * 使用 sectorKey (q:?,r:?) 以便与 VisibilitySystem 口径一致喵。
     */
    private final Map<String, Map<String, Integer>> detectorCache = new ConcurrentHashMap<>();

    /** 国家数据是否已变脏需要重算：nationId -> isDirty 喵。 */
    private final Map<String, Boolean> dirtyFlags = new ConcurrentHashMap<>();

    public IntelSystem(WorldState worldState, IntelConfigDef config) {
        this.worldState = worldState;
        this.config = config != null ? config : new IntelConfigDef();
    }

    /**
     * 获取情报系统配置喵。
     *
     * @return IntelConfigDef 配置对象
     */
    public IntelConfigDef getConfig() {
        return config;
    }

    /**
     * 标记指定国家的探测图为脏，触发重算喵。
     * 触发点：实体所有权变更、探测源实体移动、探测属性相关科技/建筑变化喵。
     */
    public void markDirty(String nationId) {
        if (nationId == null)
            return;
        dirtyFlags.put(nationId, true);
    }

    /**
     * 获取指定实体类型的情报需求等级喵。
     */
    public int getRequiredIntelLevel(EntityType type) {
        if (type == null)
            return 10;
        return config.intelRequiredLevelByEntityType.getOrDefault(type, 4);
    }

    /**
     * 判断指定国家是否能看到指定实体的细节喵。
     */
    public boolean canSeeEntity(String nationId, Entity entity) {
        if (entity == null)
            return false;

        // 0级情报需求（基础天文数据）直接可见喵
        int required = getRequiredIntelLevel(entity.entityType);
        if (required <= 0)
            return true;

        if (nationId == null || nationId.isBlank())
            return false;

        // 自己的实体永远可见喵
        if (nationId.equals(entity.ownerNationId))
            return true;

        // 从缓存中获取目标星区的有效探测等级喵
        int effectiveLevel = getEffectiveDetectorLevel(nationId, entity.sectorCoord);
        return effectiveLevel >= required;
    }

    /**
     * 获取指定国家在指定星区的有效探测等级（优先读缓存）喵。
     */
    public int getEffectiveDetectorLevel(String nationId, SectorCoord targetSector) {
        if (nationId == null || targetSector == null)
            return -1;

        rebuildIfDirty(nationId);

        Map<String, Integer> nationCache = detectorCache.get(nationId);
        if (nationCache == null)
            return -1;

        String key = coordToKey(targetSector);
        return nationCache.getOrDefault(key, -1);
    }

    /**
     * 获取指定国家的所有星区探测等级映射只读视图喵。
     * 
     * @param nationId 国家ID喵
     * @return key为"q:?,r:?", value为探测等级(0-10)的Map喵
     */
    public Map<String, Integer> getNationSectorIntelLevelsView(String nationId) {
        if (nationId == null) {
            return Map.of();
        }
        rebuildIfDirty(nationId);
        Map<String, Integer> nationCache = detectorCache.get(nationId);
        return nationCache != null ? java.util.Collections.unmodifiableMap(nationCache) : Map.of();
    }

    /**
     * 如果缓存已脏，则为指定国家重新计算全图探测分布图喵。
     * 同时更新各星区的 nationDetectorLevels 缓存，供 Webnet 快速查询喵。
     */
    private void rebuildIfDirty(String nationId) {
        if (!dirtyFlags.getOrDefault(nationId, true) && detectorCache.containsKey(nationId)) {
            return;
        }

        synchronized (this) {
            // 双重检查喵
            if (!dirtyFlags.getOrDefault(nationId, true) && detectorCache.containsKey(nationId)) {
                return;
            }

            Map<String, Integer> newMap = new HashMap<>();

            // 1. 寻找所有探测源并确定其强度与范围喵
            // 规则：按星区聚合探测源的最大基础强度与最大范围喵
            Map<SectorCoord, SourceInfo> sourceSectors = new HashMap<>();
            for (Entity e : worldState.entitiesById.values()) {
                if (nationId.equals(e.ownerNationId) && e.sectorCoord != null) {
                    Integer strength = config.detectorSourceStrengthByEntityType.get(e.entityType);
                    Integer range = config.detectorSourceRangeByEntityType.get(e.entityType);

                    if (strength != null && range != null) {
                        SourceInfo info = sourceSectors.computeIfAbsent(e.sectorCoord, k -> new SourceInfo());
                        info.maxStrength = Math.max(info.maxStrength, strength);
                        info.maxRange = Math.max(info.maxRange, range);
                    }
                }
            }

            // 2. 从每个探测源星区向外扩散影响喵
            for (Map.Entry<SectorCoord, SourceInfo> entry : sourceSectors.entrySet()) {
                SectorCoord center = entry.getKey();
                SourceInfo info = entry.getValue();

                // 遍历范围内的所有星区喵
                int range = info.maxRange;
                for (int q = -range; q <= range; q++) {
                    for (int r = -range; r <= range; r++) {
                        if (Math.abs(q + r) <= range) {
                            SectorCoord target = new SectorCoord(center.q() + q, center.r() + r);
                            // 检查该星区是否在地图内（可选优化）喵
                            if (worldState.worldMap.getSector(target) == null)
                                continue;

                            int dist = HexMath.distance(center, target);
                            int bonus = config.detectorRingBonusByDistance.getOrDefault(dist, 0);
                            int level = info.maxStrength + bonus;

                            // 裁剪并记录最高值喵
                            level = Math.max(0, Math.min(10, level));
                            String key = coordToKey(target);
                            newMap.put(key, Math.max(newMap.getOrDefault(key, -1), level));
                        }
                    }
                }
            }

            // 3. 更新星区的 nationDetectorLevels 缓存喵
            // 先清理该国家在所有星区的旧数据喵
            for (var sector : worldState.worldMap.getSectorsView()) {
                sector.nationDetectorLevels.remove(nationId);
            }
            // 再写入新的探测等级喵
            for (Map.Entry<String, Integer> entry : newMap.entrySet()) {
                SectorCoord coord = keyToCoord(entry.getKey());
                if (coord == null) continue;
                var sector = worldState.worldMap.getSector(coord);
                if (sector != null) {
                    sector.nationDetectorLevels.put(nationId, entry.getValue());
                }
            }

            detectorCache.put(nationId, newMap);
            dirtyFlags.put(nationId, false);
        }
    }

    /**
     * 获取指定国家的情报可见星区集合（有效等级 > 0）喵。
     */
    public Set<SectorCoord> computeIntelVisibleSectors(String nationId) {
        Set<SectorCoord> result = new HashSet<>();
        if (nationId == null)
            return result;

        rebuildIfDirty(nationId);
        Map<String, Integer> nationCache = detectorCache.get(nationId);
        if (nationCache != null) {
            for (Map.Entry<String, Integer> entry : nationCache.entrySet()) {
                if (entry.getValue() >= 0) { // 有效探测范围内（即使等级为0只要在范围内就算可见）喵
                    result.add(keyToCoord(entry.getKey()));
                }
            }
        }
        return result;
    }

    private String coordToKey(SectorCoord coord) {
        return "q:" + coord.q() + ",r:" + coord.r();
    }

    private SectorCoord keyToCoord(String key) {
        try {
            String[] parts = key.split(",");
            int q = Integer.parseInt(parts[0].split(":")[1]);
            int r = Integer.parseInt(parts[1].split(":")[1]);
            return new SectorCoord(q, r);
        } catch (Exception e) {
            return null;
        }
    }

    private static class SourceInfo {
        int maxStrength = -1;
        int maxRange = -1;
    }
}
