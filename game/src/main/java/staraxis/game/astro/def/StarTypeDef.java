package staraxis.game.astro.def;

import java.util.List;

/**
 * StarTypeDef
 *
 * Data model for star types loaded from assets/star/star-types.json.
 */
public class StarTypeDef {
    public String typeId;
    public String description;
    public double weight;
    public List<Double> radiusKmRange;
    public List<Double> massSolarRange;
    public List<Integer> temperatureKRange;
    public List<String> spriteCandidates;
}
