package staraxis.game.intel.def;

import java.util.Map;
import java.util.HashMap;
import staraxis.game.entity.EntityType;

/**
 * IntelConfigDef（情报系统配置定义）
 * 
 * 作用：定义探测强度、探测范围以及实体情报需求等级喵。
 * 
 * 规则：
 * 1. 探测强度不再由国家全局提供，而是由探测源实体（探测源）提供喵。
 * 2. 每个星区的有效探测等级取决于该星区内所有探测源提供的最高值喵。
 */
public class IntelConfigDef {

    /** 
     * 各实体类型提供的基础探测强度喵。
     * Key: 实体类型
     * Value: 在该实体所在星区提供的基础探测等级加成喵。
     */
    public Map<EntityType, Integer> detectorSourceStrengthByEntityType = new HashMap<>(Map.of(
        EntityType.PLANET, 5,
        EntityType.SHIP, 3
    ));

    /**
     * 各实体类型提供的探测范围（星区环数）喵。
     * 0 表示仅限当前星区，1 表示当前星区+周边一圈喵。
     */
    public Map<EntityType, Integer> detectorSourceRangeByEntityType = new HashMap<>(Map.of(
        EntityType.PLANET, 1,
        EntityType.SHIP, 1
    ));

    /** 
     * 探测等级随距离（Ring）的额外加成/衰减曲线喵。
     * Key: 距离星区数 (Ring)
     * Value: 加成值喵。
     * 例如：{0: 0, 1: 0} 表示不额外增减，仅使用实体提供的基础强度喵。
     * 按照用户需求：实体所在星区提供5探测等级（ring 0 bonus=0, source=5），外圈1格提供1探测等级（ring 1 bonus= -4, source=5）。
     * 或者直接配置为绝对值逻辑，此处采用相对于 source 的加成/衰减喵。
     */
    public Map<Integer, Integer> detectorRingBonusByDistance = new HashMap<>(Map.of(
        0, 0,
        1, -4
    ));

    /** 
     * 各实体类型所需的情报等级（需求等级）喵。
     * 0级需求永远可见喵。
     */
    public Map<EntityType, Integer> intelRequiredLevelByEntityType = new HashMap<>(Map.of(
        EntityType.STAR, 0,
        EntityType.PLANET, 0,
        EntityType.SYSTEM_BARYCENTER, 0,
        EntityType.SHIP, 4
    ));

    public IntelConfigDef() {
    }

    /**
     * 校验配置是否有效喵。
     */
    public boolean isValid() {
        return detectorSourceStrengthByEntityType != null && 
               detectorSourceRangeByEntityType != null && 
               intelRequiredLevelByEntityType != null;
    }
}
