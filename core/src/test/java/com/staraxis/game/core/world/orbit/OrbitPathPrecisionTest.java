package com.staraxis.game.core.world.orbit;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.stellar.orbit.OrbitPathSampler;
import com.staraxis.game.shared.model.Vector2;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPath;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPrecisionLevel;

public class OrbitPathPrecisionTest {

    @Test
    public void testPrecisionLevelsHaveDifferentPointCountsButSimilarShape() {
        Orbit orbit = new Orbit(new OrbitCenterRef("star_0", null), 0.3f, 0.2f, 2.5f, null);

        OrbitPathSampler sampler = new OrbitPathSampler();
        OrbitPath low = sampler.sample("orbit_test", orbit, OrbitPrecisionLevel.LOW);
        OrbitPath medium = sampler.sample("orbit_test", orbit, OrbitPrecisionLevel.MEDIUM);
        OrbitPath high = sampler.sample("orbit_test", orbit, OrbitPrecisionLevel.HIGH);

        assertTrue(low.getSamples().size() < medium.getSamples().size(), "LOW should have fewer points than MEDIUM");
        assertTrue(medium.getSamples().size() < high.getSamples().size(), "MEDIUM should have fewer points than HIGH");

        Bounds bLow = bounds(low);
        Bounds bMedium = bounds(medium);
        Bounds bHigh = bounds(high);

        assertTrue(closeRelative(bLow.width(), bMedium.width(), 0.05f));
        assertTrue(closeRelative(bLow.height(), bMedium.height(), 0.05f));
        assertTrue(closeRelative(bLow.width(), bHigh.width(), 0.05f));
        assertTrue(closeRelative(bLow.height(), bHigh.height(), 0.05f));

        String d1 = digest(sampler.sample("orbit_test", orbit, OrbitPrecisionLevel.MEDIUM));
        String d2 = digest(sampler.sample("orbit_test", orbit, OrbitPrecisionLevel.MEDIUM));
        assertEquals(d1, d2, "Same orbit + precision should be deterministic");
    }

    private static boolean closeRelative(float a, float b, float relEps) {
        float diff = Math.abs(a - b);
        float scale = Math.max(1.0e-6f, Math.max(Math.abs(a), Math.abs(b)));
        return diff / scale <= relEps;
    }

    private static Bounds bounds(OrbitPath path) {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Vector2 v : path.getSamples()) {
            minX = Math.min(minX, v.x);
            maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y);
            maxY = Math.max(maxY, v.y);
        }
        return new Bounds(minX, maxX, minY, maxY);
    }

    private static String digest(OrbitPath path) {
        Bounds b = bounds(path);
        return String.format(Locale.ROOT, "n=%d|minX=%.4f|maxX=%.4f|minY=%.4f|maxY=%.4f", path.getSamples().size(), b.minX, b.maxX, b.minY, b.maxY);
    }

    private record Bounds(float minX, float maxX, float minY, float maxY) {

        float width() {
            return maxX - minX;
        }

        float height() {
            return maxY - minY;
        }
    }
}
