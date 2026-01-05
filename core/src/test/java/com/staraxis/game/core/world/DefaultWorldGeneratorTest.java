package com.staraxis.game.core.world;

import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultWorldGeneratorTest {

    @Test
    public void testDeterminism() {
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);

        DefaultWorldGenerator generator = new DefaultWorldGenerator();
        WorldMap map1 = generator.generate(config);
        WorldMap map2 = generator.generate(config);

        assertEquals(map1.getTiles().size(), map2.getTiles().size(), "Map sizes should be equal");

        for (HexCoord coord : map1.getTiles().keySet()) {
            assertTrue(map2.getTiles().containsKey(coord), "Map 2 should contain same coords as Map 1");
            assertEquals(map1.getTile(coord).getTypeId(), map2.getTile(coord).getTypeId(),
                    "Tile types should be identical for same seed at " + coord);
            assertEquals(map1.getTile(coord).isHasHabitable(), map2.getTile(coord).isHasHabitable(),
                    "Habitable status should be identical for same seed at " + coord);
        }
    }

    @Test
    public void testDifferentSeedsYieldDifferentResults() {
        WorldGenConfig config1 = new WorldGenConfig();
        config1.setMapSizePresetId("small");
        config1.setSeedValue(1111L);
        config1.setHabitableRatio(0.5f);

        WorldGenConfig config2 = new WorldGenConfig();
        config2.setMapSizePresetId("small");
        config2.setSeedValue(2222L);
        config2.setHabitableRatio(0.5f);

        DefaultWorldGenerator generator = new DefaultWorldGenerator();
        WorldMap map1 = generator.generate(config1);
        WorldMap map2 = generator.generate(config2);

        boolean foundDifference = false;
        for (HexCoord coord : map1.getTiles().keySet()) {
            if (!map1.getTile(coord).getTypeId().equals(map2.getTile(coord).getTypeId())
                    || map1.getTile(coord).isHasHabitable() != map2.getTile(coord).isHasHabitable()) {
                foundDifference = true;
                break;
            }
        }
        assertTrue(foundDifference, "Different seeds should yield different maps");
    }
}
