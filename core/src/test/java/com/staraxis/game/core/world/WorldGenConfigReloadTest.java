package com.staraxis.game.core.world;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class WorldGenConfigReloadTest {

    @Test
    void same_seed_different_config_changes_stats_digest() {
        DefaultWorldGenerator generator = new DefaultWorldGenerator();

        WorldGenConfig a = new WorldGenConfig();
        a.setMapSizePresetId("small");
        a.setSeedValue(12345L);
        a.setStarDensity(0.6f);
        a.setPlanetComplexity(0.5f);
        a.setNebulaRatio(0.2f);

        WorldGenConfig b = new WorldGenConfig();
        b.setMapSizePresetId("small");
        b.setSeedValue(12345L);
        b.setStarDensity(0.35f);
        b.setPlanetComplexity(0.9f);
        b.setNebulaRatio(0.2f);

        WorldMap ma = generator.generate(a);
        WorldMap mb = generator.generate(b);

        assertNotNull(ma);
        assertNotNull(mb);

        WorldGenStats sa = ma.getStats();
        WorldGenStats sb = mb.getStats();
        assertNotNull(sa);
        assertNotNull(sb);

        String da = digest(sa);
        String db = digest(sb);
        assertNotEquals(da, db);
    }

    private static String digest(WorldGenStats stats) {
        return "tileCount=" + stats.getTileCount()
                + "|galaxyTileCount=" + stats.getGalaxyTileCount()
                + "|starCount=" + stats.getStarCount()
                + "|planetCount=" + stats.getPlanetCount()
                + "|sectorCounts=" + String.valueOf(stats.getSectorCounts());
    }
}
