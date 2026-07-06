package staraxis.game.astro.def;

import java.util.List;
import java.util.Map;

public class OrbitPresetDef {
    public String presetId;
    public List<Integer> planetCountRange;
    public String orbitSpacingMethod;
    public List<Double> firstOrbitGURange;
    public List<Double> orbitSeparationFactorRange;
    public List<Double> eccentricityRange;
    public List<Double> inclinationDegRange;
    public List<Integer> rotationPeriodHoursRange;
    public Map<String, Integer> planetTypeWeights;

    /**
     * 轨道分区权重表。
     * 按 maxOrbitFraction 递增排列，生成时根据 currentOrbitGU / systemRadiusGU
     * 顺次匹配第一个满足条件的分区，使用该分区的权重表。
     * 为 null 或空时回退到 planetTypeWeights。
     */
    public List<OrbitZoneWeightDef> zoneWeights;
}
