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
}
