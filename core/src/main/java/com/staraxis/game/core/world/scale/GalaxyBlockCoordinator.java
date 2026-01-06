package com.staraxis.game.core.world.scale;

import java.util.logging.Logger;

import com.staraxis.game.shared.world.scale.GalaxyBlockCoordinationResult;
import com.staraxis.game.shared.world.scale.GalaxyScaleConfig;
import com.staraxis.game.shared.world.scale.GalaxyScaleRange;
import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;

/**
 * 星系与区块协调器（Galaxy-block coordinator）。
 * 
 * 作用（Purpose）：协调星系规模配置和区块规模配置，确保生成的星系能够正确映射到对应的区块，且规模匹配合理。
 * 依赖（Dependencies）：GalaxyScaleConfig, WorldBlockScaleConfig, GalaxyBlockCoordinationResult。
 * 对外接口（Public API）：coordinate(galaxyConfig, blockConfig)。
 */
public class GalaxyBlockCoordinator {

    private static final Logger LOGGER = Logger.getLogger(GalaxyBlockCoordinator.class.getName());
    
    private static final float MIN_DENSITY_RATIO = 0.1f; // 最小密度比例
    private static final float MAX_DENSITY_RATIO = 2.0f; // 最大密度比例

    /**
     * 协调星系规模和区块规模配置。
     * 
     * @param galaxyConfig 星系规模配置
     * @param blockConfig 区块规模配置
     * @return 协调结果
     */
    public GalaxyBlockCoordinationResult coordinate(GalaxyScaleConfig galaxyConfig, WorldBlockScaleConfig blockConfig) {
        GalaxyBlockCoordinationResult result = new GalaxyBlockCoordinationResult();
        
        if (galaxyConfig == null || blockConfig == null) {
            result.addWarning("星系配置或区块配置为空，跳过协调");
            return result;
        }

        // 获取实际规模范围
        GalaxyScaleConfigLoader galaxyLoader = new GalaxyScaleConfigLoader();
        WorldBlockScaleConfigLoader blockLoader = new WorldBlockScaleConfigLoader();
        
        GalaxyScaleRange galaxyRange = null;
        if (galaxyConfig.getPresetId() != null) {
            galaxyRange = galaxyLoader.getPresetRange(galaxyConfig.getPresetId());
        } else {
            galaxyRange = galaxyConfig.getCustomRange();
        }
        
        WorldBlockScaleRange blockRange = null;
        if (blockConfig.getPresetId() != null) {
            blockRange = blockLoader.getPresetRange(blockConfig.getPresetId());
        } else {
            blockRange = blockConfig.getCustomRange();
        }
        
        if (galaxyRange == null || blockRange == null) {
            result.addWarning("无法确定规模范围，跳过协调");
            return result;
        }

        // 计算密度：星系数量 / 区块数量
        int galaxyCount = galaxyRange.getDefaultStarSystems() != null 
                ? galaxyRange.getDefaultStarSystems() 
                : (galaxyRange.getMinStarSystems() + galaxyRange.getMaxStarSystems()) / 2;
        int blockCount = blockRange.getWidth() * blockRange.getHeight();
        
        float originalDensity = blockCount > 0 ? (float) galaxyCount / blockCount : 0.0f;
        result.setOriginalDensity(originalDensity);
        
        // 检查密度是否合理
        if (originalDensity < MIN_DENSITY_RATIO) {
            // 密度过低：星系数量远小于区块数量
            result.addWarning(String.format("星系密度过低 (%.2f)，建议增加星系数量或减少区块数量", originalDensity));
            result.setAdjusted(false);
        } else if (originalDensity > MAX_DENSITY_RATIO) {
            // 密度过高：星系数量远大于区块数量
            result.addWarning(String.format("星系密度过高 (%.2f)，建议减少星系数量或增加区块数量", originalDensity));
            
            // 自动调整：降低星系数量到合理范围
            int adjustedGalaxyCount = Math.round(blockCount * MAX_DENSITY_RATIO);
            adjustedGalaxyCount = Math.max(galaxyRange.getMinStarSystems(), 
                    Math.min(adjustedGalaxyCount, galaxyRange.getMaxStarSystems()));
            
            float adjustedDensity = blockCount > 0 ? (float) adjustedGalaxyCount / blockCount : 0.0f;
            result.setAdjustedDensity(adjustedDensity);
            result.setAdjusted(true);
            
            LOGGER.info(String.format("自动调整星系数量: %d -> %d (密度: %.2f -> %.2f)", 
                    galaxyCount, adjustedGalaxyCount, originalDensity, adjustedDensity));
        } else {
            // 密度合理
            result.setAdjusted(false);
            LOGGER.fine(String.format("星系密度合理: %.2f (星系数: %d, 区块数: %d)", 
                    originalDensity, galaxyCount, blockCount));
        }

        return result;
    }
}
