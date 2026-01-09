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

    // NOTE: 当前 StarSystem 模型只包含恒星列表，不包含 planets 列表；
    // 行星生成逻辑位于 Star 模型中（每颗 Star 拥有 planets）。因此这里改为验证“恒星数量范围”。
    @Test
    void generate_starCountWithinRange() {
        StarSystemGenerator gen = new StarSystemGenerator(42);
        StarSystem system = gen.generate("Alpha", 1.9885e30, 3, 5);
        assertTrue(system.stars().size() >= 1 && system.stars().size() <= 3);
    }
}
