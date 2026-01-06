package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 规模验证配置（Scale validation configuration）。
 * 
 * 作用（Purpose）：定义规模验证的完整配置（星系规模限制、区块规模限制、性能阈值）。
 * 依赖（Dependencies）：GalaxyScaleLimits, BlockScaleLimits, PerformanceThresholds。
 * 对外接口（Public API）：getGalaxyScaleLimits/setGalaxyScaleLimits/getBlockScaleLimits/setBlockScaleLimits/getPerformanceThresholds/setPerformanceThresholds。
 */
public class ScaleValidationConfig implements Serializable {

    private GalaxyScaleLimits galaxyScaleLimits;
    private BlockScaleLimits blockScaleLimits;
    private PerformanceThresholds performanceThresholds;

    public ScaleValidationConfig() {
    }

    public GalaxyScaleLimits getGalaxyScaleLimits() {
        return galaxyScaleLimits;
    }

    public void setGalaxyScaleLimits(GalaxyScaleLimits galaxyScaleLimits) {
        if (galaxyScaleLimits == null) {
            throw new IllegalArgumentException("galaxyScaleLimits（星系规模限制）不能为空");
        }
        this.galaxyScaleLimits = galaxyScaleLimits;
    }

    public BlockScaleLimits getBlockScaleLimits() {
        return blockScaleLimits;
    }

    public void setBlockScaleLimits(BlockScaleLimits blockScaleLimits) {
        if (blockScaleLimits == null) {
            throw new IllegalArgumentException("blockScaleLimits（区块规模限制）不能为空");
        }
        this.blockScaleLimits = blockScaleLimits;
    }

    public PerformanceThresholds getPerformanceThresholds() {
        return performanceThresholds;
    }

    public void setPerformanceThresholds(PerformanceThresholds performanceThresholds) {
        if (performanceThresholds == null) {
            throw new IllegalArgumentException("performanceThresholds（性能阈值）不能为空");
        }
        this.performanceThresholds = performanceThresholds;
    }

    @Override
    public String toString() {
        return "ScaleValidationConfig{"
                + "galaxyScaleLimits=" + galaxyScaleLimits
                + ", blockScaleLimits=" + blockScaleLimits
                + ", performanceThresholds=" + performanceThresholds
                + '}';
    }
}
