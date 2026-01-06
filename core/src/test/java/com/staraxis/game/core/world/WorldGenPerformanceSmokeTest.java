package com.staraxis.game.core.world;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class WorldGenPerformanceSmokeTest {

    @Test
    void generate_world_records_stats_and_duration() {
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setStarDensity(0.6f);
        config.setPlanetComplexity(0.5f);
        config.setNebulaRatio(0.2f);

        DefaultWorldGenerator generator = new DefaultWorldGenerator();

        long start = System.currentTimeMillis();
        WorldMap telling = generator.generate(config);
        long durationMs = System.currentTimeMillis() - start;

        assertNotNull(telling);
        WorldGenStats stats = telling.getStats();
        assertNotNull(stats);

        System.out.println("WorldGenPerformanceSmokeTest: durationMs=" + durationMs
                + ", tileCount=" + stats.getTileCount()
                + ", galaxyTileCount=" + stats.getGalaxyTileCount()
                + ", starCount=" + stats.getStarCount()
                + ", planetCount=" + stats.getPlanetCount());

        assertTrue(durationMs < 10_000L);
        assertTrue(stats.getTileCount() > 0);
    }
}
