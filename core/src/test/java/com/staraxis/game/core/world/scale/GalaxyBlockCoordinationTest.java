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
import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class GalaxyBlockCoordinationTest {

    @Test
    public void testGenerateWithCoordinatedScales() {
        WorldGenerator generator = new DefaultWorldGenerator();
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        
        // 设置匹配的星系和区块规模配置
        GalaxyScaleRange galaxyRange = new GalaxyScaleRange();
        galaxyRange.setMinStarSystems(10);
        galaxyRange.setMaxStarSystems(20);
        galaxyRange.setDefaultStarSystems(15);
        
        GalaxyScaleConfig galaxyConfig = new GalaxyScaleConfig();
        galaxyConfig.setCustomRange(galaxyRange);
        config.setGalaxyScaleConfig(galaxyConfig);
        
        WorldBlockScaleRange blockRange = new WorldBlockScaleRange();
        blockRange.setWidth(50);
        blockRange.setHeight(50);
        
        WorldBlockScaleConfig blockConfig = new WorldBlockScaleConfig();
        blockConfig.setCustomRange(blockRange);
        config.setWorldBlockScaleConfig(blockConfig);
        
        WorldMap worldMap = generator.generate(config);
        
        assertNotNull(worldMap);
        WorldGenStats stats = worldMap.getStats();
        assertNotNull(stats);
        assertTrue(stats.getGalaxyTileCount() > 0);
    }

    @Test
    public void testGenerateWithMismatchedScales() {
        WorldGenerator generator = new DefaultWorldGenerator();
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        
        // 设置不匹配的配置：星系数量远大于区块数量
        GalaxyScaleRange galaxyRange = new GalaxyScaleRange();
        galaxyRange.setMinStarSystems(100);
        galaxyRange.setMaxStarSystems(200);
        galaxyRange.setDefaultStarSystems(150);
        
        GalaxyScaleConfig galaxyConfig = new GalaxyScaleConfig();
        galaxyConfig.setCustomRange(galaxyRange);
        config.setGalaxyScaleConfig(galaxyConfig);
        
        WorldBlockScaleRange blockRange = new WorldBlockScaleRange();
        blockRange.setWidth(10);
        blockRange.setHeight(10); // 只有 100 个区块
        
        WorldBlockScaleConfig blockConfig = new WorldBlockScaleConfig();
        blockConfig.setCustomRange(blockRange);
        config.setWorldBlockScaleConfig(blockConfig);
        
        // 应该能够生成，但会有警告
        WorldMap worldMap = generator.generate(config);
        
        assertNotNull(worldMap);
        WorldGenStats stats = worldMap.getStats();
        assertNotNull(stats);
    }
}
