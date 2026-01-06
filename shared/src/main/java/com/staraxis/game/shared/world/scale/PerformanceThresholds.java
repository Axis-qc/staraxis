package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 性能阈值（Performance thresholds）。
 * 
 * 作用（Purpose）：定义性能验证的阈值（最大生成时间、最大内存使用、验证策略）。
 * 依赖（Dependencies）：ValidationStrategy。
 * 对外接口（Public API）：getMaxGenerationTimeMs/setMaxGenerationTimeMs/getMaxMemoryUsageMB/setMaxMemoryUsageMB/getValidationStrategy/setValidationStrategy。
 */
public class PerformanceThresholds implements Serializable {

    private Long maxGenerationTimeMs;
    private Long maxMemoryUsageMB;
    private ValidationStrategy validationStrategy;

    public PerformanceThresholds() {
        this.validationStrategy = ValidationStrategy.WARN; // 默认策略为警告
    }

    public Long getMaxGenerationTimeMs() {
        return maxGenerationTimeMs;
    }

    public void setMaxGenerationTimeMs(Long maxGenerationTimeMs) {
        if (maxGenerationTimeMs != null && maxGenerationTimeMs <= 0) {
            throw new IllegalArgumentException("maxGenerationTimeMs（最大生成时间）必须 > 0");
        }
        this.maxGenerationTimeMs = maxGenerationTimeMs;
    }

    public Long getMaxMemoryUsageMB() {
        return maxMemoryUsageMB;
    }

    public void setMaxMemoryUsageMB(Long maxMemoryUsageMB) {
        if (maxMemoryUsageMB != null && maxMemoryUsageMB <= 0) {
            throw new IllegalArgumentException("maxMemoryUsageMB（最大内存使用）必须 > 0");
        }
        this.maxMemoryUsageMB = maxMemoryUsageMB;
    }

    public ValidationStrategy getValidationStrategy() {
        return validationStrategy;
    }

    public void setValidationStrategy(ValidationStrategy validationStrategy) {
        if (validationStrategy == null) {
            throw new IllegalArgumentException("validationStrategy（验证策略）不能为空");
        }
        this.validationStrategy = validationStrategy;
    }

    @Override
    public String toString() {
        return "PerformanceThresholds{"
                + "maxGenerationTimeMs=" + maxGenerationTimeMs
                + ", maxMemoryUsageMB=" + maxMemoryUsageMB
                + ", validationStrategy=" + validationStrategy
                + '}';
    }
}
