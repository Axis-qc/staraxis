package com.staraxis.game.core.world;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class DefaultWorldGeneratorTest {

    @Test
    public void testDeterminism() {
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        config.setPlanetComplexity(0.5f);
        config.setNebulaRatio(0.2f);

        DefaultWorldGenerator generator = new DefaultWorldGenerator();
        WorldMap map1 = generator.generate(config);
        WorldMap map2 = generator.generate(config);

        assertNotNull(map1.getStats(), "Stats should be present");
        assertNotNull(map2.getStats(), "Stats should be present");

        WorldGenStats s1 = map1.getStats();
        WorldGenStats s2 = map2.getStats();
        assertEquals(s1.getTileCount(), s2.getTileCount(), "tileCount should match");
        assertEquals(s1.getGalaxyTileCount(), s2.getGalaxyTileCount(), "galaxyTileCount should match");
        assertEquals(s1.getStarCount(), s2.getStarCount(), "starCount should match");
        assertEquals(s1.getPlanetCount(), s2.getPlanetCount(), "planetCount should match");
        assertEquals(s1.getSectorCounts(), s2.getSectorCounts(), "sectorCounts should match");

        assertEquals(map1.getTiles().size(), map2.getTiles().size(), "Map sizes should be equal");

        for (HexCoord coord : map1.getTiles().keySet()) {
            assertTrue(map2.getTiles().containsKey(coord), "Map 2 should contain same coords as Map 1");
            assertEquals(map1.getTile(coord).getTypeId(), map2.getTile(coord).getTypeId(),
                    "Tile types should be identical for same seed at " + coord);
            assertEquals(map1.getTile(coord).isHasHabitable(), map2.getTile(coord).isHasHabitable(),
                    "Habitable status should be identical for same seed at " + coord);

            StarSystem sys1 = map1.getTile(coord).getStarSystem();
            StarSystem sys2 = map2.getTile(coord).getStarSystem();
            if (sys1 == null) {
                assertNull(sys2, "StarSystem should be null consistently at " + coord);
            } else {
                assertNotNull(sys2, "StarSystem should be present consistently at " + coord);
                assertEquals(sys1.getId(), sys2.getId(), "StarSystem id should match at " + coord);
                assertEquals(sys1.getStars().size(), sys2.getStars().size(), "Star count should match at " + coord);

                assertTrue(sys1.getStars().size() >= 1 && sys1.getStars().size() <= 3,
                        "starsPerSystem should be within [1,3] at " + coord);

                for (int i = 0; i < sys1.getStars().size(); i++) {
                    Star a = sys1.getStars().get(i);
                    Star b = sys2.getStars().get(i);
                    assertEquals(a.getId(), b.getId(), "Star id should match");
                    assertEquals(a.getStarTypeId(), b.getStarTypeId(), "Star type should match");
                    assertEquals(a.getPlanets().size(), b.getPlanets().size(), "Planet count per star should match");

                    Set<Integer> seenOrbitIndex = new HashSet<>();
                    for (int p = 0; p < a.getPlanets().size(); p++) {
                        Planet pa = a.getPlanets().get(p);
                        assertNotNull(pa.getOrbitIndex(), "orbitIndex should be present");
                        assertTrue(pa.getOrbitIndex() >= 0, "orbitIndex should be >= 0");
                        assertTrue(seenOrbitIndex.add(pa.getOrbitIndex()), "orbitIndex should not duplicate");
                    }
                }
            }
        }
    }

    @Test
    public void testDifferentSeedsYieldDifferentResults() {
        WorldGenConfig config1 = new WorldGenConfig();
        config1.setMapSizePresetId("small");
        config1.setSeedValue(1111L);
        config1.setHabitableRatio(0.5f);
        config1.setStarDensity(0.6f);
        config1.setPlanetComplexity(0.5f);
        config1.setNebulaRatio(0.2f);

        WorldGenConfig config2 = new WorldGenConfig();
        config2.setMapSizePresetId("small");
        config2.setSeedValue(2222L);
        config2.setHabitableRatio(0.5f);
        config2.setStarDensity(0.6f);
        config2.setPlanetComplexity(0.5f);
        config2.setNebulaRatio(0.2f);

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

        if (!foundDifference) {
            WorldGenStats s1 = map1.getStats();
            WorldGenStats s2 = map2.getStats();
            foundDifference = s1.getStarCount() != s2.getStarCount()
                    || s1.getPlanetCount() != s2.getPlanetCount()
                    || !s1.getSectorCounts().equals(s2.getSectorCounts());
        }
        assertTrue(foundDifference, "Different seeds should yield different maps");
    }
}
