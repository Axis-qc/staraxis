package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.universegen.ParallelGalaxyGenerator;
import com.staraxis.universegen.IntermediateGalaxy;
import com.staraxis.universegen.config.UniverseGenConfig;

import java.util.Random;

/**
 * 恒星系内容生成器（StarSystemContentGenerator）。
 * 
 * 根据星区类型ID为 star-system 类型的星区实际生成恒星系内容。
 * 
 * 使用的接口: UniverseGenAdapter, ParallelGalaxyGenerator
 * 提供的接口: 为 HexSectorUniverseGenerator 提供恒星系内容生成功能
 */
public class StarSystemContentGenerator implements SectorContentGenerator {

    private final ParallelGalaxyGenerator universeGenerator;
    private final UniverseGenAdapter adapter;

    public StarSystemContentGenerator() {
        this.universeGenerator = new ParallelGalaxyGenerator();
        this.adapter = new UniverseGenAdapter();
    }

    @Override
    public boolean supports(String sectorTypeId) {
        return SectorTypes.STAR_SYSTEM.equals(sectorTypeId);
    }

    @Override
    public void generateContent(HexTile tile, WorldGenConfig config, long seedValue) {
        if (!supports(tile.getTypeId())) {
            return;
        }

        HexCoord coord = tile.getCoord();
        Random tileRandom = createTileRandom(seedValue, coord);

        UniverseGenConfig universeGenConfig = new UniverseGenConfig();
        universeGenConfig.setSeed(tileRandom.nextLong());
        universeGenConfig.setGalaxyRadiusR(0);
        universeGenConfig.setStarToDeepSpaceRatio(1.0); // 确保 universegen 内部生成 galaxy

        IntermediateGalaxy intermediate = universeGenerator.generate(universeGenConfig);
        com.staraxis.universegen.model.Galaxy generatedGalaxy = intermediate.galaxy();

        if (generatedGalaxy != null && generatedGalaxy.sectors() != null && !generatedGalaxy.sectors().isEmpty()) {
            com.staraxis.universegen.model.StarSystem generatedSystem = generatedGalaxy.sectors().get(0).starSystem();
            if (generatedSystem != null) {
                tile.setStarSystem(adapter.toSharedStarSystem(generatedSystem));
            }
        }
    }

    private Random createTileRandom(long seedValue, HexCoord coord) {
        long mixed = seedValue;
        mixed ^= ((long) coord.getX() * 73856093L);
        mixed ^= ((long) coord.getY() * 19349663L);
        mixed ^= ((long) coord.getZ() * 83492791L);
        return new Random(mixed);
    }
}
