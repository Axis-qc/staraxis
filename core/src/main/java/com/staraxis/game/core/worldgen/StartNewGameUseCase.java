package com.staraxis.game.core.worldgen;

import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.net.worldgen.ErrorEnvelope;
import com.staraxis.game.shared.net.worldgen.SchemaVersions;
import com.staraxis.game.shared.net.worldgen.StartNewGameEffectiveConfig;
import com.staraxis.game.shared.net.worldgen.StartNewGameRequest;
import com.staraxis.game.shared.net.worldgen.StartNewGameResponse;
import com.staraxis.game.shared.net.worldgen.snapshot.PlanetSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.SeedUtil;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 新游戏生成用例（StartNewGameUseCase）。
 */
public class StartNewGameUseCase {

    private final WorldGenerator worldGenerator;
    private final UniverseSnapshotMapper snapshotMapper;

    public StartNewGameUseCase() {
        this(new HexSectorUniverseGenerator(), new UniverseSnapshotMapper());
    }

    public StartNewGameUseCase(WorldGenerator worldGenerator, UniverseSnapshotMapper snapshotMapper) {
        this.worldGenerator = worldGenerator;
        this.snapshotMapper = snapshotMapper;
    }

    public StartNewGameResponse execute(StartNewGameRequest request) {
        StartNewGameResponse response = new StartNewGameResponse();
        response.setSchemaVersion(SchemaVersions.WORLDGEN_V2);

        if (request == null) {
            response.setError(new ErrorEnvelope("INVALID_JSON", "worldgen.invalid_json", "request is null"));
            return response;
        }

        String mapSizePresetId = request.getMapSizePresetId();
        if (mapSizePresetId == null || !WorldGenDefinitions.getMapPresets().containsKey(mapSizePresetId)) {
            response.setError(new ErrorEnvelope("INVALID_MAP_PRESET", "worldgen.invalid_map_preset",
                    "Unknown mapSizePresetId: " + mapSizePresetId));
            return response;
        }

        String seedText = request.getSeedText();
        long seedValue = SeedUtil.resolveSeed(seedText);

        float galaxyRatio = clamp01(request.getGalaxyRatio());
        float nebulaRatio = clamp01(request.getNebulaRatio());
        float deepSpaceRatio = clamp01(request.getDeepSpaceRatio());

        // 兜底：总和>1 归一化；总和<1 剩余补深空
        float sum = galaxyRatio + nebulaRatio + deepSpaceRatio;
        if (sum > 1.0f && sum > 0.0f) {
            galaxyRatio /= sum;
            nebulaRatio /= sum;
            deepSpaceRatio /= sum;
        }
        sum = galaxyRatio + nebulaRatio + deepSpaceRatio;
        if (sum < 1.0f) {
            deepSpaceRatio += (1.0f - sum);
        }

        float planetComplexity = clamp01(request.getPlanetComplexity());

        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId(mapSizePresetId);
        config.setSeedText(seedText);
        config.setSeedValue(seedValue);
        config.setStarDensity(galaxyRatio);
        config.setNebulaRatio(nebulaRatio);
        config.setPlanetComplexity(planetComplexity);

        StartNewGameEffectiveConfig effectiveConfig = new StartNewGameEffectiveConfig();
        effectiveConfig.setMapSizePresetId(mapSizePresetId);
        effectiveConfig.setSeedText(seedText);
        effectiveConfig.setSeedValue(seedValue);
        effectiveConfig.setGalaxyRatio(galaxyRatio);
        effectiveConfig.setNebulaRatio(nebulaRatio);
        effectiveConfig.setDeepSpaceRatio(deepSpaceRatio);
        effectiveConfig.setPlanetComplexity(planetComplexity);
        response.setEffectiveConfig(effectiveConfig);

        WorldMap worldMap = worldGenerator.generate(config);

        UniverseSnapshot snapshot = snapshotMapper.createEmpty(seedValue, worldMap.getBoundsRadius());

        List<SectorSnapshot> sectors = new ArrayList<>();
        int sectorCount = 0;
        int galaxyCount = 0;
        int starCount = 0;
        int planetCount = 0;

        for (Map.Entry<HexCoord, HexTile> e : worldMap.getTiles().entrySet()) {
            HexCoord coord = e.getKey();
            HexTile tile = e.getValue();

            String sectorType = snapshotMapper.normalizeSectorType(tile.getTypeId());

            StarSystemSnapshot starSystemSnapshot = null;
            if (SectorTypes.GALAXY.equals(sectorType)) {
                galaxyCount++;

                StarSystem sys = tile.getStarSystem();
                if (sys != null) {
                    starSystemSnapshot = new StarSystemSnapshot();
                    starSystemSnapshot.setId(sys.getId());

                    List<StarSnapshot> stars = new ArrayList<>();
                    for (Star star : sys.getStars()) {
                        StarSnapshot starSnapshot = new StarSnapshot();
                        starSnapshot.setId(star.getId());
                        starSnapshot.setStarTypeId(star.getStarTypeId() != null ? star.getStarTypeId() : "unknown");

                        List<PlanetSnapshot> planets = new ArrayList<>();
                        for (Planet planet : star.getPlanets()) {
                            PlanetSnapshot planetSnapshot = new PlanetSnapshot();
                            planetSnapshot.setId(planet.getId());
                            planetSnapshot.setPlanetTypeId(planet.getPlanetTypeId() != null ? planet.getPlanetTypeId() : "unknown");
                            planetSnapshot.setOrbitIndex(planet.getOrbitIndex() != null ? planet.getOrbitIndex() : 0);
                            planets.add(planetSnapshot);
                            planetCount++;
                        }
                        starSnapshot.setPlanets(planets);

                        stars.add(starSnapshot);
                        starCount++;
                    }
                    starSystemSnapshot.setStars(stars);
                }
            }

            sectors.add(snapshotMapper.sector(coord.getX(), coord.getY(), coord.getZ(), sectorType, starSystemSnapshot));
            sectorCount++;
        }

        snapshotMapper.setSectors(snapshot, sectors);
        snapshotMapper.setStats(snapshot, sectorCount, galaxyCount, starCount, planetCount);

        response.setWorld(snapshot);
        return response;
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
