package com.staraxis.game.core.world.scale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.DefaultWorldGenerator;
import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.scale.GalaxyScaleConfig;
import com.staraxis.game.shared.world.scale.GalaxyScaleRange;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class GalaxyScaleGenerationTest {

    @Test
    public void testGenerateWithPresetConfig() {
        WorldGenerator generator = new DefaultWorldGenerator();
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        
        // 设置星系规模配置
        GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
        GalaxyScaleConfig scaleConfig = loader.loadConfig("small", null);
        config.setGalaxyScaleConfig(scaleConfig);
        
        WorldMap worldMap = generator.generate(config);
        
        assertNotNull(worldMap);
        WorldGenStats stats = worldMap.getStats();
        assertNotNull(stats);
        assertTrue(stats.getGalaxyTileCount() > 0);
        
        // 验证生成的星系数量在预设范围内
        GalaxyScaleRange range = loader.getPresetRange("small");
        assertTrue(stats.getGalaxyTileCount() <= range.getMaxStarSystems());
    }

    @Test
    public void testGenerateWithCustomRange() {
        WorldGenerator generator = new DefaultWorldGenerator();
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        
        // 设置自定义规模范围
        GalaxyScaleRange customRange = new GalaxyScaleRange();
        customRange.setMinStarSystems(5);
        customRange.setMaxStarSystems(15);
        customRange.setDefaultStarSystems(10);
        
        GalaxyScaleConfig scaleConfig = new GalaxyScaleConfig();
        scaleConfig.setCustomRange(customRange);
        config.setGalaxyScaleConfig(scaleConfig);
        
        WorldMap worldMap = generator.generate(config);
        
        assertNotNull(worldMap);
        WorldGenStats stats = worldMap.getStats();
        assertNotNull(stats);
        assertTrue(stats.getGalaxyTileCount() > 0);
    }
}
