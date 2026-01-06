package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 星系规模限制（Galaxy scale limits）。
 * 
 * 作用（Purpose）：定义星系规模的验证限制（最小/最大恒星系统数量、最大生成时间）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：getMinStarSystems/setMinStarSystems/getMaxStarSystems/setMaxStarSystems/getMaxGenerationTimeMs/setMaxGenerationTimeMs。
 */
public class GalaxyScaleLimits implements Serializable {

    private int minStarSystems;
    private int maxStarSystems;
    private Long maxGenerationTimeMs;

    public GalaxyScaleLimits() {
    }

    public int getMinStarSystems() {
        return minStarSystems;
    }

    public void setMinStarSystems(int minStarSystems) {
        if (minStarSystems < 1) {
            throw new IllegalArgumentException("minStarSystems（最小恒星系统数量）必须 >= 1");
        }
        this.minStarSystems = minStarSystems;
    }

    public int getMaxStarSystems() {
        return maxStarSystems;
    }

    public void setMaxStarSystems(int maxStarSystems) {
        if (maxStarSystems < minStarSystems) {
            throw new IllegalArgumentException("maxStarSystems（最大恒星系统数量）必须 >= minStarSystems");
        }
        this.maxStarSystems = maxStarSystems;
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

    @Override
    public String toString() {
        return "GalaxyScaleLimits{"
                + "minStarSystems=" + minStarSystems
                + ", maxStarSystems=" + maxStarSystems
                + ", maxGenerationTimeMs=" + maxGenerationTimeMs
                + '}';
    }
}
