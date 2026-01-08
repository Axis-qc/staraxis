package com.staraxis.game.core.worldgen;

import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.universegen.GalaxyGeneratorFacade;
import com.staraxis.universegen.config.UniverseGenConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 新版世界生成器：基于六边形星区网格，并调用 universegen 生成真实宇宙。
 *
 * Phase 6（T020）：并行优先。
 * - 生成每个 HexCoord 对应的 HexTile 可并行执行
 * - 最终写入 WorldMap 在单线程汇总，避免并发写 Map
 */
public class HexSectorUniverseGenerator implements WorldGenerator {

    private final GalaxyGeneratorFacade universeGenerator;

    public HexSectorUniverseGenerator() {
        this.universeGenerator = new GalaxyGeneratorFacade();
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

        // 1) 收集所有坐标
        List<HexCoord> coords = new ArrayList<>();
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                coords.add(HexCoord.of(q, -q - r, r));
            }
        }

        // 2) 并行生成瓦片
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

                        // 目前 universegen 仍是最小实现，这里只做占位挂载，保证链路可用。
                        com.staraxis.universegen.model.Galaxy generatedGalaxy = universeGenerator.generate(universeGenConfig);
                        if (generatedGalaxy != null) {
                            StarSystem starSystem = new StarSystem();
                            starSystem.setId("system-" + coord.getX() + "-" + coord.getY());
                            List<Star> stars = new ArrayList<>();
                            Star star = new Star();
                            star.setId(starSystem.getId() + "-star-0");
                            star.setStarTypeId("unknown");
                            stars.add(star);
                            starSystem.setStars(stars);
                            tile.setStarSystem(starSystem);
                        }
                    }

                    return tile;
                })
                .toList();

        // 3) 单线程写入 WorldMap
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
