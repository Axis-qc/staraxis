package com.staraxis.game.core.world.scale;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import com.staraxis.game.shared.world.scale.BlockScaleLimits;
import com.staraxis.game.shared.world.scale.GalaxyScaleConfig;
import com.staraxis.game.shared.world.scale.GalaxyScaleLimits;
import com.staraxis.game.shared.world.scale.GalaxyScaleRange;
import com.staraxis.game.shared.world.scale.PerformanceThresholds;
import com.staraxis.game.shared.world.scale.ScaleValidationConfig;
import com.staraxis.game.shared.world.scale.ValidationResult;
import com.staraxis.game.shared.world.scale.ValidationStrategy;
import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;

/**
 * 规模配置验证器（Scale configuration validator）。
 * 
 * 作用（Purpose）：验证星系规模和区块规模配置的有效性，包括数值范围验证和性能阈值验证。
 * 依赖（Dependencies）：GalaxyScaleConfig, WorldBlockScaleConfig, ScaleValidationConfig, ValidationResult。
 * 对外接口（Public API）：validateGalaxyScale, validateBlockScale, validatePerformance, loadValidationConfig。
 */
public class ScaleConfigValidator {

    private static final Logger LOGGER = Logger.getLogger(ScaleConfigValidator.class.getName());
    private static final String VALIDATION_CONFIG_FILE = "i18n/scale-validation-config.properties";
    
    private ScaleValidationConfig validationConfig;

    public ScaleConfigValidator() {
        loadValidationConfig();
    }

    /**
     * 验证星系规模配置。
     * 
     * @param config 星系规模配置
     * @return 验证结果
     */
    public ValidationResult validateGalaxyScale(GalaxyScaleConfig config) {
        ValidationResult result = new ValidationResult(true);
        
        if (config == null) {
            result.addError("星系规模配置不能为空");
            return result;
        }

        try {
            config.validate();
        } catch (IllegalStateException e) {
            result.addError("配置验证失败: " + e.getMessage());
            return result;
        }

        // 获取实际规模范围
        GalaxyScaleRange range = getEffectiveRange(config);
        if (range == null) {
            result.addError("无法确定星系规模范围");
            return result;
        }

        // 验证数值范围
        GalaxyScaleLimits limits = validationConfig.getGalaxyScaleLimits();
        if (limits != null) {
            if (range.getMinStarSystems() < limits.getMinStarSystems()) {
                result.addError(String.format("最小恒星系统数量 %d 小于限制 %d", 
                        range.getMinStarSystems(), limits.getMinStarSystems()));
            }
            if (range.getMaxStarSystems() > limits.getMaxStarSystems()) {
                result.addError(String.format("最大恒星系统数量 %d 大于限制 %d", 
                        range.getMaxStarSystems(), limits.getMaxStarSystems()));
            }
            if (range.getMinStarSystems() > range.getMaxStarSystems()) {
                result.addError(String.format("最小恒星系统数量 %d 大于最大数量 %d", 
                        range.getMinStarSystems(), range.getMaxStarSystems()));
            }
        }

        return result;
    }

    /**
     * 验证区块规模配置。
     * 
     * @param config 区块规模配置
     * @return 验证结果
     */
    public ValidationResult validateBlockScale(WorldBlockScaleConfig config) {
        ValidationResult result = new ValidationResult(true);
        
        if (config == null) {
            result.addError("区块规模配置不能为空");
            return result;
        }

        try {
            config.validate();
        } catch (IllegalStateException e) {
            result.addError("配置验证失败: " + e.getMessage());
            return result;
        }

        // 获取实际规模范围
        WorldBlockScaleRange range = getEffectiveRange(config);
        if (range == null) {
            result.addError("无法确定区块规模范围");
            return result;
        }

        // 验证数值范围
        BlockScaleLimits limits = validationConfig.getBlockScaleLimits();
        if (limits != null) {
            if (range.getWidth() < limits.getMinWidth()) {
                result.addError(String.format("区块宽度 %d 小于限制 %d", 
                        range.getWidth(), limits.getMinWidth()));
            }
            if (range.getWidth() > limits.getMaxWidth()) {
                result.addError(String.format("区块宽度 %d 大于限制 %d", 
                        range.getWidth(), limits.getMaxWidth()));
            }
            if (range.getHeight() < limits.getMinHeight()) {
                result.addError(String.format("区块高度 %d 小于限制 %d", 
                        range.getHeight(), limits.getMinHeight()));
            }
            if (range.getHeight() > limits.getMaxHeight()) {
                result.addError(String.format("区块高度 %d 大于限制 %d", 
                        range.getHeight(), limits.getMaxHeight()));
            }
        }

        return result;
    }

    /**
     * 验证性能阈值。
     * 
     * @param galaxyConfig 星系规模配置
     * @param blockConfig 区块规模配置
     * @param estimatedTimeMs 预估生成时间（毫秒）
     * @param estimatedMemoryMB 预估内存使用（MB）
     * @return 验证结果
     */
    public ValidationResult validatePerformance(GalaxyScaleConfig galaxyConfig, 
            WorldBlockScaleConfig blockConfig, long estimatedTimeMs, long estimatedMemoryMB) {
        ValidationResult result = new ValidationResult(true);
        
        PerformanceThresholds thresholds = validationConfig.getPerformanceThresholds();
        if (thresholds == null) {
            return result; // 无性能阈值配置，跳过验证
        }

        ValidationStrategy strategy = thresholds.getValidationStrategy();
        
        // 验证生成时间
        if (thresholds.getMaxGenerationTimeMs() != null) {
            if (estimatedTimeMs > thresholds.getMaxGenerationTimeMs()) {
                String message = String.format("预估生成时间 %d ms 超过阈值 %d ms", 
                        estimatedTimeMs, thresholds.getMaxGenerationTimeMs());
                if (strategy == ValidationStrategy.REJECT) {
                    result.addError(message);
                } else {
                    result.addWarning(message);
                }
            }
        }

        // 验证内存使用
        if (thresholds.getMaxMemoryUsageMB() != null) {
            if (estimatedMemoryMB > thresholds.getMaxMemoryUsageMB()) {
                String message = String.format("预估内存使用 %d MB 超过阈值 %d MB", 
                        estimatedMemoryMB, thresholds.getMaxMemoryUsageMB());
                if (strategy == ValidationStrategy.REJECT) {
                    result.addError(message);
                } else {
                    result.addWarning(message);
                }
            }
        }

        return result;
    }

    /**
     * 获取星系规模配置的有效范围。
     */
    private GalaxyScaleRange getEffectiveRange(GalaxyScaleConfig config) {
        if (config.getPresetId() != null) {
            GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
            return loader.getPresetRange(config.getPresetId());
        } else {
            return config.getCustomRange();
        }
    }

    /**
     * 获取区块规模配置的有效范围。
     */
    private WorldBlockScaleRange getEffectiveRange(WorldBlockScaleConfig config) {
        if (config.getPresetId() != null) {
            WorldBlockScaleConfigLoader loader = new WorldBlockScaleConfigLoader();
            return loader.getPresetRange(config.getPresetId());
        } else {
            return config.getCustomRange();
        }
    }

    /**
     * 从配置文件加载验证配置。
     */
    private void loadValidationConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(VALIDATION_CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("验证配置文件未找到: " + VALIDATION_CONFIG_FILE + "，将使用默认配置");
                createDefaultValidationConfig();
                return;
            }
            props.load(is);
        } catch (IOException e) {
            LOGGER.severe("加载验证配置文件失败: " + VALIDATION_CONFIG_FILE + " - " + e.getMessage());
            createDefaultValidationConfig();
            return;
        }

        // 解析配置
        ScaleValidationConfig config = new ScaleValidationConfig();
        
        // 星系规模限制
        GalaxyScaleLimits galaxyLimits = new GalaxyScaleLimits();
        galaxyLimits.setMinStarSystems(Integer.parseInt(props.getProperty("validation.galaxy.minStarSystems", "1")));
        galaxyLimits.setMaxStarSystems(Integer.parseInt(props.getProperty("validation.galaxy.maxStarSystems", "1000")));
        String galaxyMaxTime = props.getProperty("validation.galaxy.maxGenerationTimeMs");
        if (galaxyMaxTime != null) {
            galaxyLimits.setMaxGenerationTimeMs(Long.parseLong(galaxyMaxTime));
        }
        config.setGalaxyScaleLimits(galaxyLimits);

        // 区块规模限制
        BlockScaleLimits blockLimits = new BlockScaleLimits();
        blockLimits.setMinWidth(Integer.parseInt(props.getProperty("validation.block.minWidth", "1")));
        blockLimits.setMaxWidth(Integer.parseInt(props.getProperty("validation.block.maxWidth", "10000")));
        blockLimits.setMinHeight(Integer.parseInt(props.getProperty("validation.block.minHeight", "1")));
        blockLimits.setMaxHeight(Integer.parseInt(props.getProperty("validation.block.maxHeight", "10000")));
        String blockMaxTime = props.getProperty("validation.block.maxGenerationTimeMs");
        if (blockMaxTime != null) {
            blockLimits.setMaxGenerationTimeMs(Long.parseLong(blockMaxTime));
        }
        config.setBlockScaleLimits(blockLimits);

        // 性能阈值
        PerformanceThresholds perfThresholds = new PerformanceThresholds();
        String perfMaxTime = props.getProperty("validation.performance.maxGenerationTimeMs");
        if (perfMaxTime != null) {
            perfThresholds.setMaxGenerationTimeMs(Long.parseLong(perfMaxTime));
        }
        String perfMaxMemory = props.getProperty("validation.performance.maxMemoryUsageMB");
        if (perfMaxMemory != null) {
            perfThresholds.setMaxMemoryUsageMB(Long.parseLong(perfMaxMemory));
        }
        String strategyStr = props.getProperty("validation.performance.strategy", "WARN");
        perfThresholds.setValidationStrategy(ValidationStrategy.valueOf(strategyStr));
        config.setPerformanceThresholds(perfThresholds);

        this.validationConfig = config;
        LOGGER.info("加载验证配置完成");
    }

    /**
     * 创建默认验证配置（当配置文件不存在时使用）。
     */
    private void createDefaultValidationConfig() {
        ScaleValidationConfig config = new ScaleValidationConfig();
        
        GalaxyScaleLimits galaxyLimits = new GalaxyScaleLimits();
        galaxyLimits.setMinStarSystems(1);
        galaxyLimits.setMaxStarSystems(1000);
        config.setGalaxyScaleLimits(galaxyLimits);

        BlockScaleLimits blockLimits = new BlockScaleLimits();
        blockLimits.setMinWidth(1);
        blockLimits.setMaxWidth(10000);
        blockLimits.setMinHeight(1);
        blockLimits.setMaxHeight(10000);
        config.setBlockScaleLimits(blockLimits);

        PerformanceThresholds perfThresholds = new PerformanceThresholds();
        perfThresholds.setValidationStrategy(ValidationStrategy.WARN);
        config.setPerformanceThresholds(perfThresholds);

        this.validationConfig = config;
    }

    public ScaleValidationConfig getValidationConfig() {
        return validationConfig;
    }
}
