package staraxis.game.astro.def;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OrbitZoneWeightDef（轨道分区权重定义）。
 *
 * 定义某个轨道分区内的行星类型权重表。
 * 分区按 systemRadiusGU 的比例划分，maxOrbitFraction 为该分区的上限（包含性）。
 * 分区按 maxOrbitFraction 递增排列，生成时顺序匹配第一个满足条件的区间。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrbitZoneWeightDef {

    /** 分区标识，如 "inner"、"habitable"。 */
    public String zoneId;

    /**
     * 分区最大轨道比例（包含性）。
     * 当 currentOrbitGU / systemRadiusGU <= maxOrbitFraction 时命中此分区。
     */
    public double maxOrbitFraction;

    /**
     * 行星类型权重表。
     * key = PlanetTypeDef.typeId, value = 权重。
     */
    public Map<String, Integer> planetTypeWeights;
}
