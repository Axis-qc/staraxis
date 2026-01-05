package com.staraxis.game.shared.world;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 世界生成定义加载器 (World generation definitions loader). 加载并存储地图预设、瓦片类型和技术等级预设。
 */
public class WorldGenDefinitions {

    private static final Map<String, Integer> MAP_PRESETS = new HashMap<>();
    private static final Map<String, String> TILE_TYPES = new HashMap<>();
    private static final Map<String, String> TECH_LEVEL_PRESETS = new HashMap<>();

    static {
        loadMapPresets();
        loadTileTypes();
        loadTechLevelPresets();
    }

    private static void loadMapPresets() {
        Properties props = new Properties();
        try {
            props.load(WorldGenDefinitions.class.getResourceAsStream("/worldgen/map-presets.properties"));
            for (String name : props.stringPropertyNames()) {
                MAP_PRESETS.put(name, Integer.parseInt(props.getProperty(name)));
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load map presets: " + e.getMessage());
            // Fallback defaults
            MAP_PRESETS.put("small", 5);
            MAP_PRESETS.put("medium", 10);
            MAP_PRESETS.put("large", 20);
        }
    }

    private static void loadTileTypes() {
        Properties props = new Properties();
        try {
            props.load(WorldGenDefinitions.class.getResourceAsStream("/worldgen/tile-types.properties"));
            for (String name : props.stringPropertyNames()) {
                TILE_TYPES.put(name, props.getProperty(name));
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load tile types: " + e.getMessage());
            TILE_TYPES.put("galaxy", "Galaxy");
            TILE_TYPES.put("deep_space", "Deep Space");
            TILE_TYPES.put("nebula", "Nebula");
        }
    }

    private static void loadTechLevelPresets() {
        Properties props = new Properties();
        try {
            props.load(WorldGenDefinitions.class.getResourceAsStream("/worldgen/tech-level-presets.properties"));
            for (String name : props.stringPropertyNames()) {
                TECH_LEVEL_PRESETS.put(name, props.getProperty(name));
            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load tech level presets: " + e.getMessage());
            TECH_LEVEL_PRESETS.put("primitive", "Primitive");
            TECH_LEVEL_PRESETS.put("standard", "Standard");
            TECH_LEVEL_PRESETS.put("advanced", "Advanced");
        }
    }

    public static Map<String, Integer> getMapPresets() {
        return Map.copyOf(MAP_PRESETS);
    }

    public static Map<String, String> getTileTypes() {
        return Map.copyOf(TILE_TYPES);
    }

    public static Map<String, String> getTechLevelPresets() {
        return Map.copyOf(TECH_LEVEL_PRESETS);
    }

    public static int getRadius(String presetId) {
        return MAP_PRESETS.getOrDefault(presetId, 10);
    }
}
