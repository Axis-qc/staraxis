package com.staraxis.game.core.world.scale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.scale.GalaxyBlockCoordinationResult;
import com.staraxis.game.shared.world.scale.GalaxyScaleConfig;
import com.staraxis.game.shared.world.scale.GalaxyScaleRange;
import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;

public class GalaxyBlockCoordinatorTest {

    @Test
    public void testCoordinateMatchingScales() {
        GalaxyBlockCoordinator coordinator = new GalaxyBlockCoordinator();
        
        GalaxyScaleRange galaxyRange = new GalaxyScaleRange();
        galaxyRange.setMinStarSystems(10);
        galaxyRange.setMaxStarSystems(20);
        galaxyRange.setDefaultStarSystems(15);
        
        GalaxyScaleConfig galaxyConfig = new GalaxyScaleConfig();
        galaxyConfig.setCustomRange(galaxyRange);
        
        WorldBlockScaleRange blockRange = new WorldBlockScaleRange();
        blockRange.setWidth(10);
        blockRange.setHeight(10);
        
        WorldBlockScaleConfig blockConfig = new WorldBlockScaleConfig();
        blockConfig.setCustomRange(blockRange);
        
        GalaxyBlockCoordinationResult result = coordinator.coordinate(galaxyConfig, blockConfig);
        
        assertNotNull(result);
        assertNotNull(result.getOriginalDensity());
        assertTrue(result.getOriginalDensity() > 0);
    }

    @Test
    public void testCoordinateHighDensity() {
        GalaxyBlockCoordinator coordinator = new GalaxyBlockCoordinator();
        
        GalaxyScaleRange galaxyRange = new GalaxyScaleRange();
        galaxyRange.setMinStarSystems(100);
        galaxyRange.setMaxStarSystems(200);
        galaxyRange.setDefaultStarSystems(150);
        
        GalaxyScaleConfig galaxyConfig = new GalaxyScaleConfig();
        galaxyConfig.setCustomRange(galaxyRange);
        
        WorldBlockScaleRange blockRange = new WorldBlockScaleRange();
        blockRange.setWidth(10);
        blockRange.setHeight(10); // 100 blocks
        
        WorldBlockScaleConfig blockConfig = new WorldBlockScaleConfig();
        blockConfig.setCustomRange(blockRange);
        
        GalaxyBlockCoordinationResult result = coordinator.coordinate(galaxyConfig, blockConfig);
        
        assertNotNull(result);
        assertTrue(result.hasWarnings()); // 应该产生警告
    }

    @Test
    public void testCoordinateLowDensity() {
        GalaxyBlockCoordinator coordinator = new GalaxyBlockCoordinator();
        
        GalaxyScaleRange galaxyRange = new GalaxyScaleRange();
        galaxyRange.setMinStarSystems(1);
        galaxyRange.setMaxStarSystems(5);
        galaxyRange.setDefaultStarSystems(3);
        
        GalaxyScaleConfig galaxyConfig = new GalaxyScaleConfig();
        galaxyConfig.setCustomRange(galaxyRange);
        
        WorldBlockScaleRange blockRange = new WorldBlockScaleRange();
        blockRange.setWidth(100);
        blockRange.setHeight(100); // 10000 blocks
        
        WorldBlockScaleConfig blockConfig = new WorldBlockScaleConfig();
        blockConfig.setCustomRange(blockRange);
        
        GalaxyBlockCoordinationResult result = coordinator.coordinate(galaxyConfig, blockConfig);
        
        assertNotNull(result);
        assertTrue(result.hasWarnings()); // 应该产生警告
    }

    @Test
    public void testCoordinateWithPresets() {
        GalaxyBlockCoordinator coordinator = new GalaxyBlockCoordinator();
        
        GalaxyScaleConfigLoader galaxyLoader = new GalaxyScaleConfigLoader();
        WorldBlockScaleConfigLoader blockLoader = new WorldBlockScaleConfigLoader();
        
        GalaxyScaleConfig galaxyConfig = galaxyLoader.loadConfig("small", null);
        WorldBlockScaleConfig blockConfig = blockLoader.loadConfig("small", null);
        
        GalaxyBlockCoordinationResult result = coordinator.coordinate(galaxyConfig, blockConfig);
        
        assertNotNull(result);
        assertNotNull(result.getOriginalDensity());
    }
}
