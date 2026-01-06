package com.staraxis.game.core.world.astronomical;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.PlanetSizeDefinition;
import com.staraxis.game.shared.world.astronomical.StarSizeDefinition;

/**
 * 大小预设加载器（Size Preset Loader）。
 * 
 * 作用（Purpose）：从配置文件加载恒星和行星的大小预设。
 * 实现方式：从 properties 文件加载配置，支持按类型加载大小定义。
 * 
 * 依赖（Dependencies）：StarSizeDefinition, PlanetSizeDefinition, 配置文件。
 * 对外接口（Public API）：loadStarSizeDefinition(), loadPlanetSizeDefinition()。
 */
public class SizePresetLoader {

    private static final Logger LOGGER = Logger.getLogger(SizePresetLoader.class.getName());
    private static final String STAR_SIZE_CONFIG_FILE = "i18n/star-size-config.properties";
    private static final String PLANET_SIZE_CONFIG_FILE = "i18n/planet-size-config.properties";

    private Map<String, StarSizeDefinition> starSizeCache;
    private Map<String, PlanetSizeDefinition> planetSizeCache;

    public SizePresetLoader() {
        this.starSizeCache = new HashMap<>();
        this.planetSizeCache = new HashMap<>();
        loadStarSizes();
        loadPlanetSizes();
    }

    /**
     * 加载恒星大小定义。
     * 
     * @param starTypeId 恒星类型标识
     * @return 恒星大小定义
     * @throws IllegalArgumentException 如果类型不存在或配置无效
     */
    public StarSizeDefinition loadStarSizeDefinition(String starTypeId) {
        if (starTypeId == null || starTypeId.trim().isEmpty()) {
            throw new IllegalArgumentException("恒星类型标识不能为空");
        }
        
        StarSizeDefinition def = starSizeCache.get(starTypeId.trim());
        if (def == null) {
            throw new IllegalArgumentException("未找到恒星类型: " + starTypeId);
        }
        
        return def;
    }

    /**
     * 加载行星大小定义。
     * 
     * @param planetTypeId 行星类型标识
     * @return 行星大小定义
     * @throws IllegalArgumentException 如果类型不存在或配置无效
     */
    public PlanetSizeDefinition loadPlanetSizeDefinition(String planetTypeId) {
        if (planetTypeId == null || planetTypeId.trim().isEmpty()) {
            throw new IllegalArgumentException("行星类型标识不能为空");
        }
        
        PlanetSizeDefinition def = planetSizeCache.get(planetTypeId.trim());
        if (def == null) {
            throw new IllegalArgumentException("未找到行星类型: " + planetTypeId);
        }
        
        return def;
    }

    /**
     * 从配置文件加载恒星大小预设。
     */
    private void loadStarSizes() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(STAR_SIZE_CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("恒星大小配置文件未找到: " + STAR_SIZE_CONFIG_FILE);
                return;
            }
            props.load(is);
        } catch (IOException e) {
            LOGGER.severe("加载恒星大小配置文件失败: " + STAR_SIZE_CONFIG_FILE + " - " + e.getMessage());
            return;
        }

        // 解析恒星类型大小定义：star.size.{typeId}.{property}
        Map<String, Map<String, String>> starData = new HashMap<>();
        
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("star.size.")) {
                String[] parts = key.split("\\.");
                if (parts.length >= 4) {
                    String typeId = parts[2];
                    String property = parts[3];
                    if (parts.length > 4) {
                        property = parts[3] + "." + parts[4]; // 处理 min.radius.au 或 max.radius.au
                    }
                    if (parts.length > 5) {
                        property = parts[3] + "." + parts[4] + "." + parts[5]; // 处理 default.radius.au
                    }
                    
                    starData.computeIfAbsent(typeId, k -> new HashMap<>()).put(property, props.getProperty(key));
                }
            }
        }

        // 构建恒星大小定义对象
        for (Map.Entry<String, Map<String, String>> entry : starData.entrySet()) {
            String typeId = entry.getKey();
            Map<String, String> data = entry.getValue();
            
            try {
                StarSizeDefinition def = new StarSizeDefinition();
                def.setStarTypeId(typeId);
                
                // 加载半径（可能是固定值或范围）
                if (data.containsKey("radius.au")) {
                    double radius = Double.parseDouble(data.get("radius.au"));
                    def.setRadiusInAU(AstronomicalUnit.fromAU(radius));
                } else if (data.containsKey("default.radius.au")) {
                    double radius = Double.parseDouble(data.get("default.radius.au"));
                    def.setRadiusInAU(AstronomicalUnit.fromAU(radius));
                }
                
                // 加载范围（如果存在）
                if (data.containsKey("min.radius.au")) {
                    double minRadius = Double.parseDouble(data.get("min.radius.au"));
                    def.setMinRadius(AstronomicalUnit.fromAU(minRadius));
                }
                if (data.containsKey("max.radius.au")) {
                    double maxRadius = Double.parseDouble(data.get("max.radius.au"));
                    def.setMaxRadius(AstronomicalUnit.fromAU(maxRadius));
                }
                
                def.validate();
                starSizeCache.put(typeId, def);
                LOGGER.info("加载恒星大小定义: " + typeId + " = " + def.getRadiusInAU().toAU() + " AU");
            } catch (Exception e) {
                LOGGER.warning("加载恒星大小定义失败: " + typeId + " - " + e.getMessage());
            }
        }
    }

    /**
     * 从配置文件加载行星大小预设。
     */
    private void loadPlanetSizes() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(PLANET_SIZE_CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("行星大小配置文件未找到: " + PLANET_SIZE_CONFIG_FILE);
                return;
            }
            props.load(is);
        } catch (IOException e) {
            LOGGER.severe("加载行星大小配置文件失败: " + PLANET_SIZE_CONFIG_FILE + " - " + e.getMessage());
            return;
        }

        // 解析行星类型大小定义：planet.size.{typeId}.{property}
        Map<String, Map<String, String>> planetData = new HashMap<>();
        
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("planet.size.") && !key.startsWith("planet.size.reference.")) {
                String[] parts = key.split("\\.");
                if (parts.length >= 4) {
                    String typeId = parts[2];
                    String property = parts[3];
                    if (parts.length > 4) {
                        property = parts[3] + "." + parts[4]; // 处理 min.radius.au 或 max.radius.au
                    }
                    if (parts.length > 5) {
                        property = parts[3] + "." + parts[4] + "." + parts[5]; // 处理 default.radius.au
                    }
                    
                    planetData.computeIfAbsent(typeId, k -> new HashMap<>()).put(property, props.getProperty(key));
                }
            }
        }

        // 构建行星大小定义对象
        for (Map.Entry<String, Map<String, String>> entry : planetData.entrySet()) {
            String typeId = entry.getKey();
            Map<String, String> data = entry.getValue();
            
            try {
                PlanetSizeDefinition def = new PlanetSizeDefinition();
                def.setPlanetTypeId(typeId);
                
                // 加载半径（可能是固定值或范围）
                if (data.containsKey("radius.au")) {
                    double radius = Double.parseDouble(data.get("radius.au"));
                    def.setRadiusInAU(AstronomicalUnit.fromAU(radius));
                } else if (data.containsKey("default.radius.au")) {
                    double radius = Double.parseDouble(data.get("default.radius.au"));
                    def.setRadiusInAU(AstronomicalUnit.fromAU(radius));
                }
                
                // 加载范围（如果存在）
                if (data.containsKey("min.radius.au")) {
                    double minRadius = Double.parseDouble(data.get("min.radius.au"));
                    def.setMinRadius(AstronomicalUnit.fromAU(minRadius));
                }
                if (data.containsKey("max.radius.au")) {
                    double maxRadius = Double.parseDouble(data.get("max.radius.au"));
                    def.setMaxRadius(AstronomicalUnit.fromAU(maxRadius));
                }
                
                def.validate();
                planetSizeCache.put(typeId, def);
                LOGGER.info("加载行星大小定义: " + typeId + " = " + def.getRadiusInAU().toAU() + " AU");
            } catch (Exception e) {
                LOGGER.warning("加载行星大小定义失败: " + typeId + " - " + e.getMessage());
            }
        }
    }
}
