package com.staraxis.universegen.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtremeParamsTest {

    private UniverseGenConfig buildCfg(long seed, int sectorCount, float radiusLy, double ratio) {
        UniverseGenConfig cfg = new UniverseGenConfig();
        cfg.setSeed(seed);
        cfg.setSectorCount(sectorCount);
        cfg.setHexRadiusLy(radiusLy);
        cfg.setStarToDeepSpaceRatio(ratio);
        return cfg;
    }

    @Test
    void validateStrict_throwsOnInvalid() {
        UniverseGenConfig bad = buildCfg(1, -5, -10f, 2.0);
        assertThrows(IllegalArgumentException.class, () -> ConfigValidator.validateStrict(bad));
    }

    @Test
    void sanitize_fixesInvalid() {
        UniverseGenConfig bad = buildCfg(1, 0, 0f, -0.2);
        UniverseGenConfig good = ConfigValidator.sanitize(bad);
        assertTrue(good.getSectorCount() > 0);
        assertTrue(good.getHexRadiusLy() > 0);
        assertTrue(good.getStarToDeepSpaceRatio() > 0 && good.getStarToDeepSpaceRatio() <= 1);
    }
}
