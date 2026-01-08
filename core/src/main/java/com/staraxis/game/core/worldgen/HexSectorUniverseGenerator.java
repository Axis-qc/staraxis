package com.staraxis.game.core.worldgen;

import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.universegen.GalaxyGeneratorFacade;
import com.staraxis.universegen.config.UniverseGenConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 新版世界生成器：基于六边形星区网格，并调用 universegen 生成真实宇宙。
 */
public class HexSectorUniverseGenerator implements WorldGenerator {

    private final GalaxyGeneratorFacade universeGenerator;
    private final UniverseGenAdapter adapter;

    public HexSectorUniverseGenerator() {
        this.universeGenerator = new GalaxyGeneratorFacade();
        this.adapter = new UniverseGenAdapter();
    }

    @Override
    public WorldMap generate(WorldGenConfig config) {
        long startMs = System.currentTimeMillis();

        int radius = WorldGenDefinitions.getRadius(config.getMapSizePresetId());
        WorldMap worldMap = new WorldMap(config, radius);

        SectorTypeDistributor distributor = new SectorTypeDistributor(
                config.getStarDensity(), // 临时复用 starDensity 作为 galaxyRatio
                config.getNebulaRatio()
        );

        List<HexCoord> coords = new ArrayList<>();
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                coords.add(HexCoord.of(q, -q - r, r));
            }
        }

        List<HexTile> tiles = coords
                .parallelStream()
                .map(coord -> {
                    Random tileRandom = createTileRandom(config.getSeedValue(), coord);
                    String sectorType = distributor.getSectorType(tileRandom);
                    HexTile tile = new HexTile(coord, sectorType);

                    if (SectorTypes.GALAXY.equals(sectorType)) {
                        UniverseGenConfig universeGenConfig = new UniverseGenConfig();
                        universeGenConfig.setSeed(tileRandom.nextLong());
                        universeGenConfig.setSectorCount(1);
                        universeGenConfig.setStarToDeepSpaceRatio(1.0); // 确保 universegen 内部生成 galaxy

                        com.staraxis.universegen.model.Galaxy generatedGalaxy = universeGenerator.generate(universeGenConfig);
                        
                        if (generatedGalaxy != null && generatedGalaxy.sectors() != null && !generatedGalaxy.sectors().isEmpty()) {
                            com.staraxis.universegen.model.StarSystem generatedSystem = generatedGalaxy.sectors().get(0).starSystem();
                            if (generatedSystem != null) {
                                tile.setStarSystem(adapter.toSharedStarSystem(generatedSystem));
                            }
                        }
                    }

                    return tile;
                })
                .toList();

        for (HexTile tile : tiles) {
            worldMap.addTile(tile);
        }

        long durationMs = System.currentTimeMillis() - startMs;
        System.out.println("HexSectorUniverseGenerator: radius=" + radius + ", tiles=" + tiles.size() + ", durationMs=" + durationMs);

        return worldMap;
    }

    private Random createTileRandom(long seedValue, HexCoord coord) {
        long mixed = seedValue;
        mixed ^= ((long) coord.getX() * 73856093L);
        mixed ^= ((long) coord.getY() * 19349663L);
        mixed ^= ((long) coord.getZ() * 83492791L);
        return new Random(mixed);
    }
}
