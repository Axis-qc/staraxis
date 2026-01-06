package com.staraxis.game.core.world.stellar.orbit;

import java.util.ArrayList;
import java.util.List;

import com.staraxis.game.shared.model.Vector2;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPath;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPrecisionLevel;

public class OrbitPathSampler {

    public OrbitPath sample(String orbitId, Orbit orbit, OrbitPrecisionLevel precisionLevel) {
        if (orbitId == null || orbitId.isBlank()) {
            throw new IllegalArgumentException("orbitId 不能为空");
        }
        if (precisionLevel == null) {
            throw new IllegalArgumentException("precisionLevel 不能为空");
        }
        OrbitValidator.requireValid(orbit);

        int segments = segmentCount(precisionLevel);
        List<Vector2> samples = new ArrayList<>(segments + 1);

        double a = orbit.getScale();
        double e = orbit.getEccentricity();
        double phase = orbit.getPhase();

        double p = a * (1.0 - e * e);

        for (int i = 0; i < segments; i++) {
            double t = (Math.PI * 2.0) * ((double) i / (double) segments);
            double denom = 1.0 + e * Math.cos(t);
            double r = p / denom;

            double ang = t + phase;
            float x = (float) (r * Math.cos(ang));
            float y = (float) (r * Math.sin(ang));
            samples.add(new Vector2(x, y));
        }

        if (!samples.isEmpty()) {
            Vector2 first = samples.get(0);
            samples.add(new Vector2(first.x, first.y));
        }

        return new OrbitPath(orbitId, precisionLevel, samples);
    }

    private int segmentCount(OrbitPrecisionLevel level) {
        return switch (level) {
            case LOW ->
                32;
            case MEDIUM ->
                64;
            case HIGH ->
                128;
        };
    }
}
