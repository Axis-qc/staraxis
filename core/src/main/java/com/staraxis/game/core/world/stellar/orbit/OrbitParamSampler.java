package com.staraxis.game.core.world.stellar.orbit;

import java.util.Random;

import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;

public class OrbitParamSampler {

    public Orbit samplePlanetOrbit(OrbitCenterRef centerRef, int orbitIndex, Random random) {
        if (centerRef == null) {
            throw new IllegalArgumentException("centerRef 不能为空");
        }
        if (orbitIndex < 0) {
            throw new IllegalArgumentException("orbitIndex 必须 >= 0");
        }
        if (random == null) {
            throw new IllegalArgumentException("random 不能为空");
        }

        float baseScale = 1.0f + orbitIndex * 1.25f;
        float scaleJitter = 0.85f + random.nextFloat() * 0.3f;
        float scale = baseScale * scaleJitter;

        float e = random.nextFloat() * 0.35f;
        float phase = random.nextFloat() * (float) (Math.PI * 2.0);

        return new Orbit(centerRef, e, phase, scale, null);
    }
}
