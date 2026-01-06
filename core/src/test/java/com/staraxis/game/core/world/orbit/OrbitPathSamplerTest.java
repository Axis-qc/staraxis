package com.staraxis.game.core.world.orbit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.stellar.orbit.OrbitPathSampler;
import com.staraxis.game.shared.model.Vector2;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPath;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPrecisionLevel;

public class OrbitPathSamplerTest {

    @Test
    public void testOrbitPathIsClosedAndFinite() {
        Orbit orbit = new Orbit(new OrbitCenterRef("star_0", null), 0.2f, 0.1f, 2.0f, null);

        OrbitPathSampler sampler = new OrbitPathSampler();
        OrbitPath path = sampler.sample("orbit_test", orbit, OrbitPrecisionLevel.MEDIUM);

        assertNotNull(path);
        assertNotNull(path.getSamples());

        List<Vector2> samples = path.getSamples();
        assertTrue(samples.size() >= 3, "samples should have enough points");

        Vector2 first = samples.get(0);
        Vector2 last = samples.get(samples.size() - 1);
        assertTrue(distanceSq(first, last) <= 1.0e-8f, "first and last sample should close");

        for (Vector2 v : samples) {
            assertTrue(Float.isFinite(v.x), "x should be finite");
            assertTrue(Float.isFinite(v.y), "y should be finite");
        }
    }

    private static float distanceSq(Vector2 a, Vector2 b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return dx * dx + dy * dy;
    }
}
