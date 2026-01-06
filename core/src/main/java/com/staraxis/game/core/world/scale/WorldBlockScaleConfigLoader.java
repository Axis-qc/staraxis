package com.staraxis.game.core.world.scale;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;

/**
 * 世界区块规模配置加载器（World block scale configuration loader）。
 * 
 * 作用（Purpose）：从配置文件加载世界区块规模配置，支持预设档位和自定义范围两种方式。
 * 依赖（Dependencies）：WorldBlockScaleConfig, WorldBlockScaleRange。
 * 对外接口（Public API）：loadConfig(presetId, customRange), getPresetRange(presetId)。
 */
public class WorldBlockScaleConfigLoader {

    private static final Logger LOGGER = Logger.getLogger(WorldBlockScaleConfigLoader.class.getName());
    private static final String CONFIG_FILE = "i18n/world-block-scale-config.properties";
    
    private Map<String, WorldBlockScaleRange> presetCache;

    public WorldBlockScaleConfigLoader() {
        this.presetCache = new HashMap<>();
        loadPresets();
    }

    /**
     * 加载配置：支持预设档位或自定义范围。
     * 
     * @param presetId 预设档位 ID（如 "small"/"medium"/"large"），如果为 null 则使用 customRange
     * @param customRange 自定义范围，如果为 null 则使用 presetId
     * @return 区块规模配置
     */
    public WorldBlockScaleConfig loadConfig(String presetId, WorldBlockScaleRange customRange) {
        WorldBlockScaleConfig config = new WorldBlockScaleConfig();
        
        if (presetId != null && !presetId.trim().isEmpty()) {
            // 使用预设档位
            WorldBlockScaleRange presetRange = presetCache.get(presetId.trim());
            if (presetRange == null) {
                throw new IllegalArgumentException("未找到预设档位: " + presetId);
            }
            config.setPresetId(presetId.trim());
        } else if (customRange != null) {
            // 使用自定义范围
            config.setCustomRange(customRange);
        } else {
            throw new IllegalArgumentException("presetId 和 customRange 必须指定一个");
        }
        
        config.validate();
        return config;
    }

    /**
     * 获取预设档位的规模范围（用于生成时使用）。
     * 
     * @param presetId 预设档位 ID
     * @return 规模范围
     */
    public WorldBlockScaleRange getPresetRange(String presetId) {
        return presetCache.get(presetId);
    }

    /**
     * 获取所有可用的预设档位 ID。
     * 
     * @return 预设档位 ID 集合
     */
    public java.util.Set<String> getAvailablePresets() {
        return presetCache.keySet();
    }

    /**
     * 从配置文件加载预设档位定义。
     */
    private void loadPresets() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("配置文件未找到: " + CONFIG_FILE + "，将使用空预设");
                return;
            }
            props.load(is);
        } catch (IOException e) {
            LOGGER.severe("加载配置文件失败: " + CONFIG_FILE + " - " + e.getMessage());
            return;
        }

        // 解析预设档位：block.scale.preset.{presetId}.{property}
        Map<String, Map<String, String>> presetData = new HashMap<>();
        
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("block.scale.preset.")) {
                String[] parts = key.split("\\.");
                if (parts.length >= 5) {
                    String presetId = parts[3];
                    String property = parts[4];
                    
                    presetData.computeIfAbsent(presetId, k -> new HashMap<>()).put(property, props.getProperty(key));
                }
            }
        }

        // 构建预设档位对象
        for (Map.Entry<String, Map<String, String>> entry : presetData.entrySet()) {
            String presetId = entry.getKey();
            Map<String, String> data = entry.getValue();
            
            try {
                WorldBlockScaleRange range = new WorldBlockScaleRange();
                range.setWidth(Integer.parseInt(data.get("width")));
                range.setHeight(Integer.parseInt(data.get("height")));
                if (data.containsKey("blockSize")) {
                    range.setBlockSize(Float.parseFloat(data.get("blockSize")));
                }
                
                presetCache.put(presetId, range);
                LOGGER.info("加载预设档位: " + presetId + " = " + range);
            } catch (Exception e) {
                LOGGER.warning("加载预设档位失败: " + presetId + " - " + e.getMessage());
            }
        }
    }
}
