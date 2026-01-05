package com.staraxis.game.shared.world;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class SeedUtilTest {

    @Test
    public void testDeterminism() {
        String seedText = "StarAxis_2026";
        long val1 = SeedUtil.resolveSeed(seedText);
        long val2 = SeedUtil.resolveSeed(seedText);

        assertEquals(val1, val2, "Same seed text must yield same seed value");
    }

    @Test
    public void testEmptySeedIsRandom() {
        Set<Long> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(SeedUtil.resolveSeed(""));
        }
        // Very high probability that at least some are different
        assertTrue(results.size() > 1, "Empty seed should generate different values");
    }

    @Test
    public void testNullSeedIsRandom() {
        Set<Long> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(SeedUtil.resolveSeed(null));
        }
        assertTrue(results.size() > 1, "Null seed should generate different values");
    }
}
