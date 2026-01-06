package com.staraxis.game.core.world.scale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.scale.GalaxyScaleConfig;
import com.staraxis.game.shared.world.scale.GalaxyScaleRange;
import com.staraxis.game.shared.world.scale.ValidationResult;
import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;

public class ScaleConfigValidatorTest {

    @Test
    public void testValidateGalaxyScaleWithPreset() {
        ScaleConfigValidator validator = new ScaleConfigValidator();
        GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
        
        GalaxyScaleConfig config = loader.loadConfig("small", null);
        ValidationResult result = validator.validateGalaxyScale(config);
        
        assertNotNull(result);
        assertTrue(result.isValid() || result.hasWarnings()); // 应该有效或只有警告
    }

    @Test
    public void testValidateGalaxyScaleWithCustomRange() {
        ScaleConfigValidator validator = new ScaleConfigValidator();
        
        GalaxyScaleRange customRange = new GalaxyScaleRange();
        customRange.setMinStarSystems(10);
        customRange.setMaxStarSystems(100);
        
        GalaxyScaleConfig config = new GalaxyScaleConfig();
        config.setCustomRange(customRange);
        
        ValidationResult result = validator.validateGalaxyScale(config);
        
        assertNotNull(result);
        assertTrue(result.isValid() || result.hasWarnings());
    }

    @Test
    public void testValidateGalaxyScaleInvalid() {
        ScaleConfigValidator validator = new ScaleConfigValidator();
        
        GalaxyScaleRange customRange = new GalaxyScaleRange();
        customRange.setMinStarSystems(1000); // 超出默认限制
        customRange.setMaxStarSystems(2000);
        
        GalaxyScaleConfig config = new GalaxyScaleConfig();
        config.setCustomRange(customRange);
        
        ValidationResult result = validator.validateGalaxyScale(config);
        
        assertNotNull(result);
        // 应该无效或至少有一个错误/警告
        assertTrue(!result.isValid() || result.hasErrors() || result.hasWarnings());
    }

    @Test
    public void testValidateBlockScaleWithPreset() {
        ScaleConfigValidator validator = new ScaleConfigValidator();
        WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
        
        WorldBlockScaleConfig config = loader.loadConfig("small", null);
        ValidationResult result = validator.validateBlockScale(config);
        
        assertNotNull(result);
        assertTrue(result.isValid() || result.hasWarnings());
    }

    @Test
    public void testValidateBlockScaleWithCustomRange() {
        ScaleConfigValidator validator = new ScaleConfigValidator();
        
        WorldBlockScaleRange customRange = new WorldBlockScaleRange();
        customRange.setWidth(100);
        customRange.setHeight(100);
        
        WorldBlockScaleConfig config = new WorldBlockScaleConfig();
        config.setCustomRange(customRange);
        
        ValidationResult result = validator.validateBlockScale(config);
        
        assertNotNull(result);
        assertTrue(result.isValid() || result.hasWarnings());
    }

    @Test
    public void testValidateBlockScaleInvalid() {
        ScaleConfigValidator validator = new ScaleConfigValidator();
        
        WorldBlockScaleRange customRange = new WorldBlockScaleRange();
        customRange.setWidth(20000); // 超出默认限制
        customRange.setHeight(20000);
        
        WorldBlockScaleConfig config = new WorldBlockScaleConfig();
        config.setCustomRange(customRange);
        
        ValidationResult result = validator.validateBlockScale(config);
        
        assertNotNull(result);
        // 应该无效或至少有一个错误/警告
        assertTrue(!result.isValid() || result.hasErrors() || result.hasWarnings());
    }

    @Test
    public void testValidatePerformance() {
        ScaleConfigValidator validator = new ScaleConfigValidator();
        GalaxyScaleConfigLoader galaxyLoader = new GalaxyScaleConfigLoader();
        WorldBlockScaleConfigLoader blockLoader = new WorldBlockScaleConfigLoader();
        
        GalaxyScaleConfig galaxyConfig = galaxyLoader.loadConfig("small", null);
        WorldBlockScaleConfig blockConfig = blockLoader.loadConfig("small", null);
        
        ValidationResult result = validator.validatePerformance(galaxyConfig, blockConfig, 1000, 50);
        
        assertNotNull(result);
        // 性能验证应该通过或只有警告
        assertTrue(result.isValid() || result.hasWarnings());
    }
}
