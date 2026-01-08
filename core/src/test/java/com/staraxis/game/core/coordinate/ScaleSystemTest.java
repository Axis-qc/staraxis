package com.staraxis.game.core.coordinate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScaleSystemTest {

    @Test
    void zoomOneShouldYieldOneKmPerPixelWithinTolerance() {
        ScaleSystem scaleSystem = new ScaleSystem(1.0);
        double kmPerPixel = scaleSystem.getKmPerPixel();
        // Spec SC-3: 误差不超过 0.1%
        assertEquals(1.0, kmPerPixel, 0.001);
    }

    @Test
    void zoomShouldBeFiniteAndPositive() {
        ScaleSystem scaleSystem = new ScaleSystem();
        assertThrows(IllegalArgumentException.class, () -> scaleSystem.setZoom(0));
        assertThrows(IllegalArgumentException.class, () -> scaleSystem.setZoom(-1));
        assertThrows(IllegalArgumentException.class, () -> scaleSystem.setZoom(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> scaleSystem.setZoom(Double.POSITIVE_INFINITY));
    }
}
