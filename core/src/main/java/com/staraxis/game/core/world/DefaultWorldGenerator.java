package com.staraxis.game.core.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import com.staraxis.game.core.world.stellar.StellarGenerator;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenDiagnostics;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

/**
 * 默认世界生成器实现 (Default world generator implementation). 保证在相同种子和配置下生成确定的地图。
 */
public class DefaultWorldGenerator implements WorldGenerator {

    private static final Logger LOGGER = Logger.getLogger(DefaultWorldGenerator.class.getName());

    @Override
    public WorldMap generate(WorldGenConfig config) {
        long startTime = System.currentTimeMillis();
        int radius = WorldGenDefinitions.getRadius(config.getMapSizePresetId());
        WorldMap worldMap = new WorldMap(config, radius);

        StellarGenerator stellarGenerator = new StellarGenerator();
        Map<String, Integer> sectorCounts = new HashMap<>();
        int tileCount = 0;
        int galaxyTileCount = 0;
        int starCount = 0;
        int planetCount = 0;
        int starsPerSystemMin = Integer.MAX_VALUE;
        int starsPerSystemMax = Integer.MIN_VALUE;

        // 遍历六边形范围生成瓦片 (T025)
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                HexCoord coord = HexCoord.of(q, -q - r, r);

                Random tileRandom = createTileRandom(config.getSeedValue(), coord);
                float roll = tileRandom.nextFloat();
                String typeId = sampleSectorTypeId(config, roll);

                HexTile tile = new HexTile(coord, typeId);
                sectorCounts.merge(typeId, 1, Integer::sum);

                if ("galaxy".equals(typeId)) {
                    galaxyTileCount++;

                    if (tileRandom.nextFloat() < config.getHabitableRatio()) {
                        tile.setHasHabitable(true);
                    }

                    StarSystem starSystem = stellarGenerator.generateStarSystem(coord, config, tileRandom);
                    tile.setStarSystem(starSystem);

                    WorldGenDiagnostics diagnostics = starSystem.getDiagnostics();
                    if (diagnostics != null) {
                        boolean hasDiagnostics = diagnostics.getRepairAttemptCount() > 0
                                || !diagnostics.getMessages().isEmpty()
                                || !diagnostics.getDetails().isEmpty();
                        LOGGER.info(String.format(
                                "StarSystem diagnostics: systemId=%s, repairAttempts=%d, messageCount=%d, detailCount=%d, hasDiagnostics=%b",
                                starSystem.getId(), diagnostics.getRepairAttemptCount(), diagnostics.getMessages().size(), diagnostics.getDetails().size(), hasDiagnostics));
                    }

                    int starsInSystem = starSystem.getStars().size();
                    starsPerSystemMin = Math.min(starsPerSystemMin, starsInSystem);
                    starsPerSystemMax = Math.max(starsPerSystemMax, starsInSystem);
                    starCount += starsInSystem;

                    for (Star star : starSystem.getStars()) {
                        planetCount += star.getPlanets().size();
                    }
                }

                worldMap.addTile(tile);
                tileCount++;
            }
        }

        WorldGenStats stats = new WorldGenStats();
        stats.setTileCount(tileCount);
        stats.setSectorCounts(sectorCounts);
        stats.setGalaxyTileCount(galaxyTileCount);
        stats.setStarCount(starCount);
        stats.setPlanetCount(planetCount);
        if (galaxyTileCount > 0) {
            stats.setStarsPerSystemMinMax("min=" + starsPerSystemMin + ",max=" + starsPerSystemMax);
        } else {
            stats.setStarsPerSystemMinMax("min=0,max=0");
        }
        worldMap.setStats(stats);

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info(String.format(
                "Generated world: radius=%d, tiles=%d, duration=%dms, seed=%d, sectorCounts=%s, starCount=%d, planetCount=%d",
                radius, tileCount, duration, config.getSeedValue(), sectorCounts, starCount, planetCount));

        return worldMap;
    }

    private Random createTileRandom(long seedValue, HexCoord coord) {
        long mixed = seedValue;
        mixed ^= ((long) coord.getX() * 73856093L);
        mixed ^= ((long) coord.getY() * 19349663L);
        mixed ^= ((long) coord.getZ() * 83492791L);
        return new Random(mixed);
    }

    private String sampleSectorTypeId(WorldGenConfig config, float roll) {
        float g = clamp01(config.getStarDensity());
        float n = clamp01(config.getNebulaRatio());

        float pGalaxy;
        float pNebula;
        if (g + n <= 1.0f) {
            pGalaxy = g;
            pNebula = n;
        } else {
            float sum = g + n;
            pGalaxy = g / sum;
            pNebula = n / sum;
        }

        if (roll < pGalaxy) {
            return "galaxy";
        }
        if (roll < pGalaxy + pNebula) {
            return "nebula";
        }
        return "deep_space";
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
