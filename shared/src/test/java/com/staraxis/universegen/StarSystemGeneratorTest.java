package com.staraxis.universegen;

import com.staraxis.universegen.model.StarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StarSystemGeneratorTest {

    @Test
    void orbitalPeriod_accuracy() {
        // Earth around Sun baseline: semi-major 1 AU = 149.6e6 km, period ~365.25 days
        double semiMajorKm = 1.496e8;
        double sunMassKg = 1.9885e30;
        double period = StarSystemGenerator.orbitalPeriodSeconds(semiMajorKm, sunMassKg);
        double expected = 365.25 * 86400;
        assertEquals(expected, period, expected * 0.02, "期望误差 <2%");
    }

    @Test
    void generate_planetCountWithinRange() {
        StarSystemGenerator gen = new StarSystemGenerator(42);
        StarSystem system = gen.generate("Alpha", 1.9885e30, 3, 5);
        assertTrue(system.planets().size() >= 3 && system.planets().size() <=5);
    }
}
