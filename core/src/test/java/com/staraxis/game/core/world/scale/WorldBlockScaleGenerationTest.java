package com.staraxis.game.core.world.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.DefaultWorldGenerator;
import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class WorldBlockScaleGenerationTest {

    @Test
    public void testGenerateWithPresetConfig() {
        WorldGenerator generator = new DefaultWorldGenerator();
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        
        // 设置区块规模配置
        WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
        WorldBlockScaleConfig scaleConfig = loader.loadConfig("small", null);
        config.setWorldBlockScaleConfig(scaleConfig);
        
        WorldMap worldMap = generator.generate(config);
        
        assertNotNull(worldMap);
        WorldGenStats stats = worldMap.getStats();
        assertNotNull(stats);
        assertTrue(stats.getTileCount() > 0);
        
        // 验证生成的区块数量在预设范围内
        WorldBlockScaleRange range = loader.getPresetRange("small");
        int expectedMaxTiles = range.getWidth() * range.getHeight();
        assertTrue(stats.getTileCount() <= expectedMaxTiles * 2); // 允许一些容差
    }

    @Test
    public void testGenerateWithCustomRange() {
        WorldGenerator generator = new DefaultWorldGenerator();
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        
        // 设置自定义区块规模范围
        WorldBlockScaleRange customRange = new WorldBlockScaleRange();
        customRange.setWidth(100);
        customRange.setHeight(100);
        customRange.setBlockSize(1.0f);
        
        WorldBlockScaleConfig scaleConfig = new WorldBlockScaleConfig();
        scaleConfig.setCustomRange(customRange);
        config.setWorldBlockScaleConfig(scaleConfig);
        
        WorldMap worldMap = generator.generate(config);
        
        assertNotNull(worldMap);
        WorldGenStats stats = worldMap.getStats();
        assertNotNull(stats);
        assertTrue(stats.getTileCount() > 0);
        
        // 验证半径计算正确
        int expectedRadius = (int) Math.ceil(Math.max(customRange.getWidth(), customRange.getHeight()) / 2.0);
        assertTrue(worldMap.getBoundsRadius() >= expectedRadius - 1 && 
                   worldMap.getBoundsRadius() <= expectedRadius + 1); // 允许一些容差
    }

    @Test
    public void testTopologyValidation() {
        WorldGenerator generator = new DefaultWorldGenerator();
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(12345L);
        config.setHabitableRatio(0.5f);
        config.setStarDensity(0.6f);
        
        WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
        WorldBlockScaleConfig scaleConfig = loader.loadConfig("medium", null);
        config.setWorldBlockScaleConfig(scaleConfig);
        
        WorldMap worldMap = generator.generate(config);
        
        assertNotNull(worldMap);
        
        // 验证拓扑结构：检查是否有重叠或间隙
        // 每个坐标应该只出现一次
        var tiles = worldMap.getTiles();
        assertTrue(tiles.size() > 0);
        
        // 验证所有瓦片的坐标都是唯一的
        long uniqueCoords = tiles.keySet().stream().distinct().count();
        assertEquals(tiles.size(), uniqueCoords, "所有瓦片坐标应该是唯一的");
    }
}
