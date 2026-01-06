package com.staraxis.game.core.world.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;

public class WorldBlockScaleConfigLoaderTest {

    @Test
    public void testLoadPresetConfig() {
        WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
        
        WorldBlockScaleConfig config = loader.loadConfig("small", null);
        
        assertNotNull(config);
        assertEquals("small", config.getPresetId());
        assertNotNull(loader.getPresetRange("small"));
    }

    @Test
    public void testLoadCustomRange() {
        WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
        
        WorldBlockScaleRange customRange = new WorldBlockScaleRange();
        customRange.setWidth(100);
        customRange.setHeight(100);
        customRange.setBlockSize(1.0f);
        
        WorldBlockScaleConfig config = loader.loadConfig(null, customRange);
        
        assertNotNull(config);
        assertNotNull(config.getCustomRange());
        assertEquals(100, config.getCustomRange().getWidth());
        assertEquals(100, config.getCustomRange().getHeight());
    }

    @Test
    public void testLoadInvalidPreset() {
        WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
        
        assertThrows(IllegalArgumentException.class, () -> {
            loader.loadConfig("invalid_preset", null);
        });
    }

    @Test
    public void testGetAvailablePresets() {
        WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
        
        var presets = loader.getAvailablePresets();
        assertTrue(presets.size() > 0);
        assertTrue(presets.contains("small") || presets.contains("medium") || presets.contains("large"));
    }
}
