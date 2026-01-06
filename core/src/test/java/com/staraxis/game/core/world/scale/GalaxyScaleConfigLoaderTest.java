package com.staraxis.game.core.world.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.scale.GalaxyScaleConfig;
import com.staraxis.game.shared.world.scale.GalaxyScaleRange;

public class GalaxyScaleConfigLoaderTest {

    @Test
    public void testLoadPresetConfig() {
        GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
        
        GalaxyScaleConfig config = loader.loadConfig("small", null);
        
        assertNotNull(config);
        assertEquals("small", config.getPresetId());
        assertNotNull(loader.getPresetRange("small"));
    }

    @Test
    public void testLoadCustomRange() {
        GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
        
        GalaxyScaleRange customRange = new GalaxyScaleRange();
        customRange.setMinStarSystems(10);
        customRange.setMaxStarSystems(50);
        
        GalaxyScaleConfig config = loader.loadConfig(null, customRange);
        
        assertNotNull(config);
        assertNotNull(config.getCustomRange());
        assertEquals(10, config.getCustomRange().getMinStarSystems());
        assertEquals(50, config.getCustomRange().getMaxStarSystems());
    }

    @Test
    public void testLoadInvalidPreset() {
        GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
        
        assertThrows(IllegalArgumentException.class, () -> {
            loader.loadConfig("invalid_preset", null);
        });
    }

    @Test
    public void testGetAvailablePresets() {
        GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
        
        var presets = loader.getAvailablePresets();
        assertTrue(presets.size() > 0);
        assertTrue(presets.contains("small") || presets.contains("medium") || presets.contains("large"));
    }
}
