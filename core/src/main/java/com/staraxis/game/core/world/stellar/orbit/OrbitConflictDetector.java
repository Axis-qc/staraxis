package com.staraxis.game.core.world.stellar.orbit;

import java.util.List;

import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;

public final class OrbitConflictDetector {

    private OrbitConflictDetector() {
    }

    public static String findFirstConflictReason(List<Orbit> orbits, float minScaleSeparation) {
        if (orbits == null || orbits.size() < 2) {
            return null;
        }
        if (!Float.isFinite(minScaleSeparation) || minScaleSeparation < 0.0f) {
            throw new IllegalArgumentException("minScaleSeparation 必须为有限且 >= 0");
        }

        for (int i = 0; i < orbits.size(); i++) {
            Orbit a = orbits.get(i);
            if (a == null) {
                continue;
            }
            for (int j = i + 1; j < orbits.size(); j++) {
                Orbit b = orbits.get(j);
                if (b == null) {
                    continue;
                }

                if (!isSameCenter(a.getCenterRef(), b.getCenterRef())) {
                    continue;
                }

                float da = a.getScale();
                float db = b.getScale();
                if (!Float.isFinite(da) || !Float.isFinite(db)) {
                    return "轨道尺度包含非有限数值";
                }

                float diff = Math.abs(da - db);
                if (diff < minScaleSeparation) {
                    return "轨道尺度间距过小: diff=" + diff + ", min=" + minScaleSeparation;
                }
            }
        }

        return null;
    }

    public static boolean hasConflict(List<Orbit> orbits, float minScaleSeparation) {
        return findFirstConflictReason(orbits, minScaleSeparation) != null;
    }

    private static boolean isSameCenter(OrbitCenterRef a, OrbitCenterRef b) {
        if (a == null || b == null) {
            return false;
        }
        String aStarId = a.getStarId();
        String bStarId = b.getStarId();
        if (aStarId != null && bStarId != null) {
            return aStarId.equals(bStarId);
        }
        String aBary = a.getBarycenterId();
        String bBary = b.getBarycenterId();
        if (aBary != null && bBary != null) {
            return aBary.equals(bBary);
        }
        return false;
    }
}
