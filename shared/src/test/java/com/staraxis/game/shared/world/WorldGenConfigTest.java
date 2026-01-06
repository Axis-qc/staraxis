package com.staraxis.game.shared.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * 世界生成配置测试（WorldGenConfigTest）。
 *
 * 作用（Purpose）：验证 WorldGenConfig 的数值型字段在 setter 中会被 clamp 到 [0,1]。
 * 依赖（Dependencies）：JUnit 5。 对外接口（Public API）：测试用例。
 */
public class WorldGenConfigTest {

    @Test
    public void testClampNewFields() {
        WorldGenConfig config = new WorldGenConfig();

        config.setStarDensity(-1.0f);
        assertEquals(0.0f, config.getStarDensity(), 0.0001f);
        config.setStarDensity(2.0f);
        assertEquals(1.0f, config.getStarDensity(), 0.0001f);

        config.setPlanetComplexity(-1.0f);
        assertEquals(0.0f, config.getPlanetComplexity(), 0.0001f);
        config.setPlanetComplexity(2.0f);
        assertEquals(1.0f, config.getPlanetComplexity(), 0.0001f);

        config.setNebulaRatio(-1.0f);
        assertEquals(0.0f, config.getNebulaRatio(), 0.0001f);
        config.setNebulaRatio(2.0f);
        assertEquals(1.0f, config.getNebulaRatio(), 0.0001f);
    }
}
