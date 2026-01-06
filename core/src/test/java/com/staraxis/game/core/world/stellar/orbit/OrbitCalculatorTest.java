package com.staraxis.game.core.world.stellar.orbit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector2;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;

public class OrbitCalculatorTest {

    @Test
    public void testCalculatePosition() {
        OrbitCalculator calculator = new OrbitCalculator();
        
        Orbit orbit = new Orbit();
        orbit.setCenterRef(new OrbitCenterRef("star1", null));
        orbit.setSemiMajorAxis(10.0f);
        orbit.setEccentricity(0.1f);
        orbit.setPhase(0.0f);
        
        Vector2 position = calculator.calculatePosition(orbit, 1.0f, 0.0f);
        
        assertNotNull(position);
        assertTrue(Float.isFinite(position.x));
        assertTrue(Float.isFinite(position.y));
    }

    @Test
    public void testCalculatePeriod() {
        OrbitCalculator calculator = new OrbitCalculator();
        
        Orbit orbit = new Orbit();
        orbit.setCenterRef(new OrbitCenterRef("star1", null));
        orbit.setSemiMajorAxis(10.0f);
        orbit.setEccentricity(0.1f);
        
        float period = calculator.calculatePeriod(orbit, 1.0f);
        
        assertTrue(Float.isFinite(period));
        assertTrue(period > 0);
    }

    @Test
    public void testCalculateTrueAnomaly() {
        OrbitCalculator calculator = new OrbitCalculator();
        
        float E = 1.0f; // 偏近点角
        float e = 0.1f; // 偏心率
        
        float nu = calculator.calculateTrueAnomaly(E, e);
        
        assertTrue(Float.isFinite(nu));
        assertTrue(nu >= 0 && nu < 2.0 * Math.PI);
    }
}
