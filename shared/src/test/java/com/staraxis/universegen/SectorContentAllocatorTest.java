package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SectorContentAllocatorTest {

    @Test
    void allocationRatios_withinTenPercentTolerance_forLargeGalaxy() {
        UniverseGenConfig cfg = new UniverseGenConfig();
        cfg.setSeed(12345);
        cfg.setGalaxyRadiusR(8); // N = 1 + 3*8*9 = 217, which is >= 200
        cfg.setHexRadiusLy(1f);

        Map<String, Double> ratios = Map.of(
                "star-system", 0.5,
                "nebula", 0.2,
                "deep_space", 0.3
        );
        cfg.setContentRatios(ratios);

        SectorLocatorService locator = new SectorLocatorService(cfg.getHexRadiusLy());
        List<Long> sectorIds = locator.generateSectorIdsByRadius(cfg.getGalaxyRadiusR());

        // No presets for this test
        Map<Long, String> presetOccupancy = Map.of();

        Map<Long, String> allocation = SectorContentAllocator.allocate(cfg, sectorIds, presetOccupancy);

        assertEquals(sectorIds.size(), allocation.size(), "Allocation should cover all sectors");

        // Count actual types
        long starSystemCount = allocation.values().stream().filter(t -> t.equals("star-system")).count();
        long nebulaCount = allocation.values().stream().filter(t -> t.equals("nebula")).count();
        long deepSpaceCount = allocation.values().stream().filter(t -> t.equals("deep_space")).count();

        // Calculate actual ratios
        double total = allocation.size();
        double actualStarSystemRatio = starSystemCount / total;
        double actualNebulaRatio = nebulaCount / total;
        double actualDeepSpaceRatio = deepSpaceCount / total;

        // Assert with 10% tolerance (as per SC-003)
        assertRatioWithinTolerance(0.5, actualStarSystemRatio, 0.1, "star-system");
        assertRatioWithinTolerance(0.2, actualNebulaRatio, 0.1, "nebula");
        assertRatioWithinTolerance(0.3, actualDeepSpaceRatio, 0.1, "deep_space");
    }

    private void assertRatioWithinTolerance(double expected, double actual, double tolerance, String type) {
        assertTrue(Math.abs(expected - actual) <= tolerance,
                String.format("Ratio for '%s' is out of tolerance. Expected=%.2f, Actual=%.2f, Tolerance=%.2f",
                        type, expected, actual, tolerance));
    }
}
