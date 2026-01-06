package com.staraxis.game.core.worldgen;

import com.staraxis.game.core.world.DefaultWorldGenerator;
import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.net.worldgen.ErrorEnvelope;
import com.staraxis.game.shared.net.worldgen.SchemaVersions;
import com.staraxis.game.shared.net.worldgen.StartNewGameEffectiveConfig;
import com.staraxis.game.shared.net.worldgen.StartNewGameRequest;
import com.staraxis.game.shared.net.worldgen.StartNewGameResponse;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldSnapshot;
import com.staraxis.game.shared.world.SeedUtil;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldMap;

public class StartNewGameUseCase {

    private final WorldGenerator worldGenerator;
    private final WorldSnapshotMapper snapshotMapper;

    public StartNewGameUseCase() {
        this(new DefaultWorldGenerator(), new WorldSnapshotMapper());
    }

    public StartNewGameUseCase(WorldGenerator worldGenerator, WorldSnapshotMapper snapshotMapper) {
        this.worldGenerator = worldGenerator;
        this.snapshotMapper = snapshotMapper;
    }

    public StartNewGameResponse execute(StartNewGameRequest request) {
        StartNewGameResponse response = new StartNewGameResponse();
        response.setSchemaVersion(SchemaVersions.WORLDGEN_V1);

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

        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId(mapSizePresetId);
        config.setSeedText(seedText);
        config.setSeedValue(seedValue);

        float habitableRatio = clamp01(request.getHabitableRatio());
        float starDensity = clamp01(request.getStarDensity());
        float planetComplexity = clamp01(request.getPlanetComplexity());
        float nebulaRatio = clamp01(request.getNebulaRatio());

        config.setHabitableRatio(habitableRatio);
        config.setStarDensity(starDensity);
        config.setPlanetComplexity(planetComplexity);
        config.setNebulaRatio(nebulaRatio);

        StartNewGameEffectiveConfig effectiveConfig = new StartNewGameEffectiveConfig();
        effectiveConfig.setMapSizePresetId(mapSizePresetId);
        effectiveConfig.setSeedText(seedText);
        effectiveConfig.setSeedValue(seedValue);
        effectiveConfig.setHabitableRatio(habitableRatio);
        effectiveConfig.setStarDensity(starDensity);
        effectiveConfig.setPlanetComplexity(planetComplexity);
        effectiveConfig.setNebulaRatio(nebulaRatio);
        response.setEffectiveConfig(effectiveConfig);

        WorldMap worldMap = worldGenerator.generate(config);
        WorldSnapshot snapshot = snapshotMapper.toSnapshot(worldMap);
        response.setWorld(snapshot);

        return response;
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
