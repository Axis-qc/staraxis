package com.staraxis.game.client.ui.view.astronomical;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.VisualScaleConfig;

/**
 * 天文单位渲染转换器（Astronomical Scale Renderer）。
 * 
 * 作用（Purpose）：处理逻辑单位（AU）到渲染单位（像素）的转换，实现自动缩放逻辑。
 * 实现方式：基于 VisualScaleConfig 配置，根据视图范围自动计算缩放因子。
 * 
 * 依赖（Dependencies）：AstronomicalUnit, VisualScaleConfig。
 * 对外接口（Public API）：convertToPixels(), calculateAutoScaleFactor()。
 */
public class AstronomicalScaleRenderer {

    private VisualScaleConfig visualScaleConfig;
    private float currentViewWidth; // 当前视图宽度（AU）
    private float currentViewHeight; // 当前视图高度（AU）

    /**
     * 构造函数。
     * 
     * @param visualScaleConfig 可视化缩放配置
     */
    public AstronomicalScaleRenderer(VisualScaleConfig visualScaleConfig) {
        if (visualScaleConfig == null) {
            throw new IllegalArgumentException("可视化缩放配置不能为空");
        }
        this.visualScaleConfig = visualScaleConfig;
    }

    /**
     * 将逻辑单位（AU）转换为渲染单位（像素）。
     * 
     * @param auValue 逻辑单位值（AU）
     * @return 渲染单位值（像素）
     */
    public float convertToPixels(AstronomicalUnit auValue) {
        if (auValue == null) {
            return 0.0f;
        }
        
        float effectiveScale = getEffectiveScaleFactor();
        return (float) (auValue.toAU() * visualScaleConfig.getAuToPixels() * effectiveScale);
    }

    /**
     * 将逻辑单位（AU）转换为渲染单位（像素），应用实体类型的基础缩放。
     * 
     * @param auValue 逻辑单位值（AU）
     * @param entityType 实体类型（"star", "planet", "orbit"）
     * @return 渲染单位值（像素）
     */
    public float convertToPixels(AstronomicalUnit auValue, String entityType) {
        if (auValue == null) {
            return 0.0f;
        }
        
        float baseScale = getBaseScaleForEntity(entityType);
        float effectiveScale = getEffectiveScaleFactor();
        return (float) (auValue.toAU() * visualScaleConfig.getAuToPixels() * effectiveScale * baseScale);
    }

    /**
     * 获取有效缩放因子（自动或手动）。
     * 
     * @return 有效缩放因子
     */
    public float getEffectiveScaleFactor() {
        if (visualScaleConfig.isAutoScaleEnabled()) {
            return calculateAutoScaleFactor();
        } else {
            return visualScaleConfig.getManualScaleFactor();
        }
    }

    /**
     * 计算自动缩放因子。
     * 根据当前视图范围自动计算合适的缩放因子，确保内容在视图中可见。
     * 
     * @return 自动缩放因子
     */
    public float calculateAutoScaleFactor() {
        if (currentViewWidth <= 0.0f || currentViewHeight <= 0.0f) {
            return 1.0f; // 默认缩放因子
        }
        
        // 计算视图对角线（AU）
        double viewDiagonal = Math.sqrt(currentViewWidth * currentViewWidth + currentViewHeight * currentViewHeight);
        
        // 假设目标渲染尺寸为 1000 像素（可配置）
        float targetPixels = 1000.0f;
        
        // 计算缩放因子：targetPixels / (viewDiagonal * auToPixels)
        float scaleFactor = (float) (targetPixels / (viewDiagonal * visualScaleConfig.getAuToPixels()));
        
        // 限制在允许的范围内
        float minFactor = visualScaleConfig.getAutoMinFactor();
        float maxFactor = visualScaleConfig.getAutoMaxFactor();
        scaleFactor = Math.max(minFactor, Math.min(maxFactor, scaleFactor));
        
        return scaleFactor;
    }

    /**
     * 设置当前视图范围。
     * 
     * @param viewWidth 视图宽度（AU）
     * @param viewHeight 视图高度（AU）
     */
    public void setViewRange(float viewWidth, float viewHeight) {
        if (viewWidth <= 0.0f || viewHeight <= 0.0f) {
            throw new IllegalArgumentException("视图范围必须 > 0，当前值: " + viewWidth + " x " + viewHeight);
        }
        this.currentViewWidth = viewWidth;
        this.currentViewHeight = viewHeight;
    }

    /**
     * 获取实体类型的基础缩放比例。
     * 
     * @param entityType 实体类型（"star", "planet", "orbit"）
     * @return 基础缩放比例
     */
    private float getBaseScaleForEntity(String entityType) {
        if (entityType == null) {
            return 1.0f;
        }
        
        switch (entityType.toLowerCase()) {
            case "star":
                return visualScaleConfig.getStarBaseScale();
            case "planet":
                return visualScaleConfig.getPlanetBaseScale();
            case "orbit":
                return visualScaleConfig.getOrbitBaseScale();
            default:
                return 1.0f;
        }
    }

    /**
     * 更新可视化缩放配置。
     * 
     * @param visualScaleConfig 新的可视化缩放配置
     */
    public void updateVisualScaleConfig(VisualScaleConfig visualScaleConfig) {
        if (visualScaleConfig == null) {
            throw new IllegalArgumentException("可视化缩放配置不能为空");
        }
        this.visualScaleConfig = visualScaleConfig;
    }
}
