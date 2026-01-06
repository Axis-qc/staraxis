package com.staraxis.game.core.world.stellar.orbit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;
import com.staraxis.game.shared.world.stellar.orbit.OrbitStabilityCheckResult;

public class OrbitStabilityCheckerTest {

    @Test
    public void testCheckStability() {
        OrbitStabilityChecker checker = new OrbitStabilityChecker();
        
        Orbit orbit = new Orbit();
        orbit.setCenterRef(new OrbitCenterRef("star1", null));
        orbit.setSemiMajorAxis(10.0f);
        orbit.setEccentricity(0.1f);
        
        OrbitStabilityCheckResult result = checker.checkStability(orbit, 1.0f, new ArrayList<>(), 0.1f);
        
        assertNotNull(result);
        assertNotNull(result.getMinDistance());
        assertNotNull(result.getOrbitalEnergy());
    }

    @Test
    public void testCalculateMinDistance() {
        OrbitStabilityChecker checker = new OrbitStabilityChecker();
        
        Orbit orbit = new Orbit();
        orbit.setCenterRef(new OrbitCenterRef("star1", null));
        orbit.setSemiMajorAxis(10.0f);
        orbit.setEccentricity(0.1f);
        
        float minDistance = checker.calculateMinDistance(orbit, new ArrayList<>(), 0.1f);
        
        assertTrue(Float.isFinite(minDistance));
        assertTrue(minDistance > 0);
    }

    @Test
    public void testCalculateOrbitalEnergy() {
        OrbitStabilityChecker checker = new OrbitStabilityChecker();
        
        Orbit orbit = new Orbit();
        orbit.setCenterRef(new OrbitCenterRef("star1", null));
        orbit.setSemiMajorAxis(10.0f);
        orbit.setEccentricity(0.1f);
        
        float energy = checker.calculateOrbitalEnergy(orbit, 1.0f);
        
        assertTrue(Float.isFinite(energy));
        assertTrue(energy < 0); // 束缚轨道能量应为负值
    }

    @Test
    public void testCheckStabilityWithMultipleOrbits() {
        OrbitStabilityChecker checker = new OrbitStabilityChecker();
        
        Orbit orbit1 = new Orbit();
        orbit1.setCenterRef(new OrbitCenterRef("star1", null));
        orbit1.setSemiMajorAxis(10.0f);
        orbit1.setEccentricity(0.1f);
        
        Orbit orbit2 = new Orbit();
        orbit2.setCenterRef(new OrbitCenterRef("star1", null));
        orbit2.setSemiMajorAxis(12.0f);
        orbit2.setEccentricity(0.1f);
        
        List<Orbit> otherOrbits = new ArrayList<>();
        otherOrbits.add(orbit2);
        
        OrbitStabilityCheckResult result = checker.checkStability(orbit1, 1.0f, otherOrbits, 0.1f);
        
        assertNotNull(result);
    }
}
