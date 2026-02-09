package staraxis.game.astro.def;

import java.util.List;
import java.util.Map;

public class PlanetTypeDef {
    public String typeId;
    public String description;
    public double weight;
    public List<Double> radiusGURange;
    public List<String> spriteCandidates;

    /**
     * 该星球类型允许生成的地表区域类型及其权重分配喵。
     * Key 为 SurfaceRegionTypeDef.typeId，Value 为生成权重喵。
     */
    public Map<String, Double> surfaceRegionWeights;
}
