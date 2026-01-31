package staraxis.game.astro.def;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
