package staraxis.game.astro.def;

import java.util.List;
import java.util.Map;

/**
 * StarTypeDef
 *
 * Data model for star types loaded from assets/star/star-types.json.
 */
public class StarTypeDef {
    public String typeId;
    public String description;
    public double weight;
    public List<Double> radiusGURange;
    public List<Double> massSolarRange;
    public List<Integer> temperatureKRange;
    public List<String> spriteCandidates;

    /** 恒星系半径范围（GU），用于轨道生成的基准。 */
    public List<Double> systemRadiusGURange;

    /**
     * 该恒星类型周围可能出现的行星类型及其权重。
     * key = PlanetTypeDef.typeId, value = 权重。
     * 如果为 null 或空，则使用 OrbitPreset 中的全局权重。
     */
    public Map<String, Integer> planetTypeWeights;
}
