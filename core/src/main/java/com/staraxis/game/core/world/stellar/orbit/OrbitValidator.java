package com.staraxis.game.core.world.stellar.orbit;

import java.util.ArrayList;
import java.util.List;

import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;

public final class OrbitValidator {

    private OrbitValidator() {
    }

    public static List<String> validateOrbit(Orbit orbit) {
        List<String> errors = new ArrayList<>();
        if (orbit == null) {
            errors.add("orbit 不能为空");
            return errors;
        }

        OrbitCenterRef centerRef = orbit.getCenterRef();
        if (centerRef == null) {
            errors.add("centerRef 不能为空");
        }

        float e = orbit.getEccentricity();
        if (!Float.isFinite(e) || e < 0.0f || e >= 1.0f) {
            errors.add("eccentricity 必须满足 0 <= e < 1 且为有限数值");
        }

        float scale = orbit.getScale();
        if (!Float.isFinite(scale) || scale <= 0.0f) {
            errors.add("scale 必须 > 0 且为有限数值");
        }

        float phase = orbit.getPhase();
        if (!Float.isFinite(phase)) {
            errors.add("phase 必须为有限数值");
        }

        Float inclination = orbit.getInclination();
        if (inclination != null && !Float.isFinite(inclination)) {
            errors.add("inclination 必须为有限数值");
        }

        return errors;
    }

    public static void requireValid(Orbit orbit) {
        List<String> errors = validateOrbit(orbit);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }
}
