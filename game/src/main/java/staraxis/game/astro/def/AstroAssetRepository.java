package staraxis.game.astro.def;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AstroAssetRepository
 *
 * Reads astro definitions from assets/ (allowed IO for game).
 */
public class AstroAssetRepository {

    private final ObjectMapper objectMapper;

    private List<StarTypeDef> starTypes = List.of();

    private List<PlanetTypeDef> planetTypes = List.of();

    private OrbitPresetDef orbitPreset;

    public AstroAssetRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void loadAll() {
        starTypes = readList("assets/star/star-types.json", StarTypeDef[].class);
        planetTypes = readList("assets/planet/planet-types.json", PlanetTypeDef[].class);

        OrbitPresetsFile f = readObj("assets/planet/orbit-presets.json", OrbitPresetsFile.class);
        if (f != null && f.presets != null && !f.presets.isEmpty()) {
            orbitPreset = f.presets.get(0);
        } else {
            orbitPreset = null;
        }
    }

    public List<StarTypeDef> getStarTypes() {
        return Collections.unmodifiableList(starTypes);
    }

    public List<PlanetTypeDef> getPlanetTypes() {
        return Collections.unmodifiableList(planetTypes);
    }

    public OrbitPresetDef getOrbitPreset() {
        return orbitPreset;
    }

    /**
     * 按 typeId 查找恒星类型定义。
     *
     * @param typeId 恒星类型 ID
     * @return 匹配的 StarTypeDef，未找到返回 null
     */
    public StarTypeDef getStarType(String typeId) {
        for (StarTypeDef t : starTypes) {
            if (t.typeId.equals(typeId)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 按 typeId 查找行星类型定义。
     *
     * @param typeId 行星类型 ID
     * @return 匹配的 PlanetTypeDef，未找到返回 null
     */
    public PlanetTypeDef getPlanetType(String typeId) {
        for (PlanetTypeDef t : planetTypes) {
            if (t.typeId.equals(typeId)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取指定恒星类型的行星权重表。
     * 如果恒星类型定义了专属权重表则使用之，否则使用全局 OrbitPreset 的权重。
     *
     * @param starTypeId 恒星类型 ID
     * @return 行星类型到权重的映射，不会为 null
     */
    public Map<String, Integer> getPlanetWeightsForStarType(String starTypeId) {
        StarTypeDef starType = getStarType(starTypeId);
        if (starType != null && starType.planetTypeWeights != null && !starType.planetTypeWeights.isEmpty()) {
            return starType.planetTypeWeights;
        }
        if (orbitPreset != null && orbitPreset.planetTypeWeights != null) {
            return orbitPreset.planetTypeWeights;
        }
        return Collections.emptyMap();
    }

    private <T> T readObj(String path, Class<T> clazz) {
        try {
            File file = new File(path);
            if (!file.isFile()) {
                return null;
            }
            return objectMapper.readValue(file, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<T> readList(String path, Class<?> arrayClazz) {
        try {
            File file = new File(path);
            if (!file.isFile()) {
                return List.of();
            }
            Object arr = objectMapper.readValue(file, arrayClazz);
            if (!(arr instanceof Object[])) {
                return List.of();
            }
            Object[] a = (Object[]) arr;
            ArrayList<T> out = new ArrayList<>(a.length);
            for (Object o : a) {
                @SuppressWarnings("unchecked")
                T t = (T) o;
                out.add(t);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
