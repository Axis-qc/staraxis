package com.staraxis.game.shared.world.scale;

import java.io.Serializable;

/**
 * 星系规模范围（Galaxy scale range）。
 * 
 * 作用（Purpose）：定义星系规模的数量范围（最小/最大/默认恒星系统数量）。
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：getMinStarSystems/setMinStarSystems/getMaxStarSystems/setMaxStarSystems/getDefaultStarSystems/setDefaultStarSystems。
 */
public class GalaxyScaleRange implements Serializable {

    private int minStarSystems;
    private int maxStarSystems;
    private Integer defaultStarSystems;

    public GalaxyScaleRange() {
    }

    public GalaxyScaleRange(int minStarSystems, int maxStarSystems) {
        setMinStarSystems(minStarSystems);
        setMaxStarSystems(maxStarSystems);
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

    public Integer getDefaultStarSystems() {
        return defaultStarSystems;
    }

    public void setDefaultStarSystems(Integer defaultStarSystems) {
        if (defaultStarSystems != null) {
            if (defaultStarSystems < minStarSystems || defaultStarSystems > maxStarSystems) {
                throw new IllegalArgumentException(
                        "defaultStarSystems（默认恒星系统数量）必须在 [minStarSystems, maxStarSystems] 范围内");
            }
        }
        this.defaultStarSystems = defaultStarSystems;
    }

    @Override
    public String toString() {
        return "GalaxyScaleRange{"
                + "minStarSystems=" + minStarSystems
                + ", maxStarSystems=" + maxStarSystems
                + ", defaultStarSystems=" + defaultStarSystems
                + '}';
    }
}
