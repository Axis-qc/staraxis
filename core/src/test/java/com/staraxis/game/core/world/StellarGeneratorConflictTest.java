package com.staraxis.game.core.world;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.stellar.StellarGenerator;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenDiagnostics;

public class StellarGeneratorConflictTest {

    @Test
    public void testOrbitRepairStopsAfterThreeAttemptsAndReturnsDiagnostics() {
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        config.setPlanetComplexity(0.0f);
        config.setNebulaRatio(0.2f);
        Random random = new FirstThenConstantFloatRandom(0.80f, 0.0f);

        StellarGenerator generator = new StellarGenerator();
        StarSystem system = generator.generateStarSystem(HexCoord.of(0, 0, 0), config, random);

        assertNotNull(system);
        assertEquals(2, system.getStars().size(), "Expected binary star system");

        WorldGenDiagnostics diagnostics = system.getDiagnostics();
        assertNotNull(diagnostics, "Diagnostics should be present when conflict repair is attempted");
        assertEquals(3, diagnostics.getRepairAttemptCount(), "Repair attempts should be capped at 3");
        assertTrue(diagnostics.getMessages().size() > 0, "Diagnostics should contain messages");

        boolean hasUnresolvedMessage = false;
        for (String msg : diagnostics.getMessages()) {
            if (msg != null && msg.contains("unresolved") && msg.contains("3")) {
                hasUnresolvedMessage = true;
                break;
            }
        }
        assertTrue(hasUnresolvedMessage, "Should contain unresolved conflict message after 3 attempts");
    }

    private static final class FirstThenConstantFloatRandom extends Random {

        private final float first;
        private final float constant;
        private boolean firstUsed;

        private FirstThenConstantFloatRandom(float first, float constant) {
            this.first = first;
            this.constant = constant;
        }

        @Override
        public float nextFloat() {
            if (!firstUsed) {
                firstUsed = true;
                return first;
            }
            return constant;
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
