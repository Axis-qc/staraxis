package com.staraxis.game.core.world;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.stellar.StellarGenerator;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;

public class StellarGeneratorHierarchyTest {

    @Test
    public void testBinarySystemHasBarycenterAndCircumbinaryPlanet() {
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        config.setPlanetComplexity(0.5f);
        config.setNebulaRatio(0.2f);

        Random random = new FloatSequenceRandom(
                0.80f,
                0.00f,
                0.00f,
                0.10f,
                0.00f,
                1.00f,
                0.00f,
                0.00f,
                0.00f,
                1.00f,
                0.00f,
                0.00f,
                0.00f,
                1.00f,
                0.00f,
                0.00f,
                0.00f,
                1.00f,
                0.00f,
                0.00f,
                0.00f,
                1.00f,
                0.00f,
                0.00f,
                0.00f
        );

        StellarGenerator generator = new StellarGenerator();
        StarSystem system = generator.generateStarSystem(HexCoord.of(0, 0, 0), config, random);

        assertNotNull(system);
        assertEquals(2, system.getStars().size(), "Expected binary star system");
        assertTrue(system.getBarycenterIds().size() >= 1, "Binary system should have barycenter id(s)");

        boolean foundCircumbinaryPlanet = false;
        boolean foundNonCircularOrbit = false;

        for (Star star : system.getStars()) {
            for (Planet planet : star.getPlanets()) {
                Orbit orbit = planet.getOrbit();
                assertNotNull(orbit, "Orbit should be present");
                if (orbit.getCenterRef() != null && orbit.getCenterRef().getBarycenterId() != null) {
                    foundCircumbinaryPlanet = true;
                }
                if (orbit.getEccentricity() > 0.0f) {
                    foundNonCircularOrbit = true;
                }
            }
        }

        assertTrue(foundCircumbinaryPlanet, "Should have at least one circumbinary planet");
        assertTrue(foundNonCircularOrbit, "Should have at least one non-circular orbit (eccentricity > 0)");
    }

    private static final class FloatSequenceRandom extends Random {

        private final float[] floats;
        private int floatIndex;

        private FloatSequenceRandom(float... floats) {
            this.floats = floats == null ? new float[0] : floats;
        }

        @Override
        public float nextFloat() {
            if (floatIndex >= floats.length) {
                return 0.0f;
            }
            return floats[floatIndex++];
        }

        @Override
        public double nextGaussian() {
            return 0.0;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
