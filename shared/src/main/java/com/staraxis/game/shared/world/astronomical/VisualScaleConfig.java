package com.staraxis.game.shared.world.astronomical;

import java.io.Serializable;

/**
 * 可视化缩放配置（Visual Scale Configuration）。
 * 
 * 作用（Purpose）：定义逻辑单位到渲染单位的转换比例和可视化缩放参数。
 * 实现方式：支持自动缩放和手动缩放，提供不同实体类型的基础缩放比例。
 * 
 * 依赖（Dependencies）：无。
 * 对外接口（Public API）：getAuToPixels(), getEffectiveScaleFactor(), 
 * setManualScaleFactor(), resetToAuto()。
 */
public class VisualScaleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * AU 到像素的转换比例。
     * 默认值：0.00079（1 AU = 0.00079 像素）
     */
    private float auToPixels;

    /**
     * 是否启用自动缩放。
     * 如果为 true，根据当前视图自动计算缩放因子；如果为 false，使用手动设置的缩放因子。
     */
    private boolean autoScaleEnabled;

    /**
     * 手动缩放因子。
     * 当 autoScaleEnabled 为 false 时使用。
     */
    private float manualScaleFactor;

    /**
     * 恒星基础缩放比例。
     */
    private float starBaseScale;

    /**
     * 行星基础缩放比例。
     */
    private float planetBaseScale;

    /**
     * 轨道基础缩放比例。
     */
    private float orbitBaseScale;

    /**
     * 自动缩放的最小因子。
     */
    private float autoMinFactor;

    /**
     * 自动缩放的最大因子。
     */
    private float autoMaxFactor;

    /**
     * 默认构造函数，使用默认值。
     */
    public VisualScaleConfig() {
        this.auToPixels = 0.00079f;
        this.autoScaleEnabled = true;
        this.manualScaleFactor = 1.0f;
        this.starBaseScale = 1.0f;
        this.planetBaseScale = 0.5f;
        this.orbitBaseScale = 1.0f;
        this.autoMinFactor = 0.1f;
        this.autoMaxFactor = 10.0f;
    }

    /**
     * 构造函数，指定所有参数。
     */
    public VisualScaleConfig(float auToPixels, boolean autoScaleEnabled, 
                             float manualScaleFactor, float starBaseScale,
                             float planetBaseScale, float orbitBaseScale,
                             float autoMinFactor, float autoMaxFactor) {
        setAuToPixels(auToPixels);
        this.autoScaleEnabled = autoScaleEnabled;
        setManualScaleFactor(manualScaleFactor);
        setStarBaseScale(starBaseScale);
        setPlanetBaseScale(planetBaseScale);
        setOrbitBaseScale(orbitBaseScale);
        setAutoMinFactor(autoMinFactor);
        setAutoMaxFactor(autoMaxFactor);
    }

    /**
     * 获取 AU 到像素的转换比例。
     * 
     * @return AU 到像素的转换比例
     */
    public float getAuToPixels() {
        return auToPixels;
    }

    /**
     * 设置 AU 到像素的转换比例。
     * 
     * @param auToPixels AU 到像素的转换比例
     * @throws IllegalArgumentException 如果比例 <= 0
     */
    public void setAuToPixels(float auToPixels) {
        if (auToPixels <= 0.0f || !Float.isFinite(auToPixels)) {
            throw new IllegalArgumentException("AU 到像素的转换比例必须 > 0 且为有限数值，当前值: " + auToPixels);
        }
        this.auToPixels = auToPixels;
    }

    /**
     * 是否启用自动缩放。
     * 
     * @return 是否启用自动缩放
     */
    public boolean isAutoScaleEnabled() {
        return autoScaleEnabled;
    }

    /**
     * 设置是否启用自动缩放。
     * 
     * @param autoScaleEnabled 是否启用自动缩放
     */
    public void setAutoScaleEnabled(boolean autoScaleEnabled) {
        this.autoScaleEnabled = autoScaleEnabled;
    }

    /**
     * 获取手动缩放因子。
     * 
     * @return 手动缩放因子
     */
    public float getManualScaleFactor() {
        return manualScaleFactor;
    }

    /**
     * 设置手动缩放因子。
     * 
     * @param factor 手动缩放因子
     * @throws IllegalArgumentException 如果因子 <= 0
     */
    public void setManualScaleFactor(float factor) {
        if (factor <= 0.0f || !Float.isFinite(factor)) {
            throw new IllegalArgumentException("手动缩放因子必须 > 0 且为有限数值，当前值: " + factor);
        }
        this.manualScaleFactor = factor;
    }

    /**
     * 获取有效缩放因子（自动或手动）。
     * 
     * @return 有效缩放因子
     */
    public float getEffectiveScaleFactor() {
        if (autoScaleEnabled) {
            // 自动缩放：根据当前视图计算（这里返回默认值，实际实现由渲染器完成）
            // 实际实现应该在 AstronomicalScaleRenderer 中根据视图范围计算
            return 1.0f; // 占位值，实际由渲染器计算
        } else {
            return manualScaleFactor;
        }
    }

    /**
     * 重置为自动缩放。
     */
    public void resetToAuto() {
        this.autoScaleEnabled = true;
        this.manualScaleFactor = 1.0f;
    }

    /**
     * 获取恒星基础缩放比例。
     * 
     * @return 恒星基础缩放比例
     */
    public float getStarBaseScale() {
        return starBaseScale;
    }

    /**
     * 设置恒星基础缩放比例。
     * 
     * @param starBaseScale 恒星基础缩放比例
     * @throws IllegalArgumentException 如果比例 <= 0
     */
    public void setStarBaseScale(float starBaseScale) {
        if (starBaseScale <= 0.0f || !Float.isFinite(starBaseScale)) {
            throw new IllegalArgumentException("恒星基础缩放比例必须 > 0 且为有限数值，当前值: " + starBaseScale);
        }
        this.starBaseScale = starBaseScale;
    }

    /**
     * 获取行星基础缩放比例。
     * 
     * @return 行星基础缩放比例
     */
    public float getPlanetBaseScale() {
        return planetBaseScale;
    }

    /**
     * 设置行星基础缩放比例。
     * 
     * @param planetBaseScale 行星基础缩放比例
     * @throws IllegalArgumentException 如果比例 <= 0
     */
    public void setPlanetBaseScale(float planetBaseScale) {
        if (planetBaseScale <= 0.0f || !Float.isFinite(planetBaseScale)) {
            throw new IllegalArgumentException("行星基础缩放比例必须 > 0 且为有限数值，当前值: " + planetBaseScale);
        }
        this.planetBaseScale = planetBaseScale;
    }

    /**
     * 获取轨道基础缩放比例。
     * 
     * @return 轨道基础缩放比例
     */
    public float getOrbitBaseScale() {
        return orbitBaseScale;
    }

    /**
     * 设置轨道基础缩放比例。
     * 
     * @param orbitBaseScale 轨道基础缩放比例
     * @throws IllegalArgumentException 如果比例 <= 0
     */
    public void setOrbitBaseScale(float orbitBaseScale) {
        if (orbitBaseScale <= 0.0f || !Float.isFinite(orbitBaseScale)) {
            throw new IllegalArgumentException("轨道基础缩放比例必须 > 0 且为有限数值，当前值: " + orbitBaseScale);
        }
        this.orbitBaseScale = orbitBaseScale;
    }

    /**
     * 获取自动缩放的最小因子。
     * 
     * @return 自动缩放的最小因子
     */
    public float getAutoMinFactor() {
        return autoMinFactor;
    }

    /**
     * 设置自动缩放的最小因子。
     * 
     * @param autoMinFactor 自动缩放的最小因子
     */
    public void setAutoMinFactor(float autoMinFactor) {
        if (autoMinFactor <= 0.0f || !Float.isFinite(autoMinFactor)) {
            throw new IllegalArgumentException("自动缩放最小因子必须 > 0 且为有限数值，当前值: " + autoMinFactor);
        }
        this.autoMinFactor = autoMinFactor;
    }

    /**
     * 获取自动缩放的最大因子。
     * 
     * @return 自动缩放的最大因子
     */
    public float getAutoMaxFactor() {
        return autoMaxFactor;
    }

    /**
     * 设置自动缩放的最大因子。
     * 
     * @param autoMaxFactor 自动缩放的最大因子
     */
    public void setAutoMaxFactor(float autoMaxFactor) {
        if (autoMaxFactor <= 0.0f || !Float.isFinite(autoMaxFactor)) {
            throw new IllegalArgumentException("自动缩放最大因子必须 > 0 且为有限数值，当前值: " + autoMaxFactor);
        }
        this.autoMaxFactor = autoMaxFactor;
    }

    @Override
    public String toString() {
        return "VisualScaleConfig{auToPixels=" + auToPixels + 
               ", autoScaleEnabled=" + autoScaleEnabled + 
               ", manualScaleFactor=" + manualScaleFactor + 
               ", starBaseScale=" + starBaseScale + 
               ", planetBaseScale=" + planetBaseScale + 
               ", orbitBaseScale=" + orbitBaseScale + "}";
    }
}
