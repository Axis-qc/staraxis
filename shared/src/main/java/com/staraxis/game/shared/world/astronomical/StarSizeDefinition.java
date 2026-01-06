package com.staraxis.game.shared.world.astronomical;

import java.io.Serializable;

/**
 * 恒星大小定义（Star Size Definition）。
 * 
 * 作用（Purpose）：定义恒星半径的大小，基于天文单位，按类型分类。
 * 实现方式：使用 AstronomicalUnit 表示恒星半径，支持按类型加载配置，支持范围验证。
 * 
 * 依赖（Dependencies）：AstronomicalUnit。
 * 对外接口（Public API）：getRadiusInAU(), setRadiusInAU(), loadFromConfig(), validate()。
 */
public class StarSizeDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 恒星类型标识（如 "yellow_dwarf", "red_giant"）。
     */
    private String starTypeId;

    /**
     * 恒星半径（以 AU 为单位）。
     * 必须 > 0，且在 [minRadius, maxRadius] 范围内（如果定义了范围）。
     */
    private AstronomicalUnit radiusInAU;

    /**
     * 该类型的最小半径（可选）。
     */
    private AstronomicalUnit minRadius;

    /**
     * 该类型的最大半径（可选）。
     */
    private AstronomicalUnit maxRadius;

    /**
     * 默认构造函数。
     */
    public StarSizeDefinition() {
    }

    /**
     * 构造函数，指定恒星类型和半径。
     * 
     * @param starTypeId 恒星类型标识
     * @param radiusInAU 恒星半径（AU）
     */
    public StarSizeDefinition(String starTypeId, AstronomicalUnit radiusInAU) {
        this.starTypeId = starTypeId;
        setRadiusInAU(radiusInAU);
    }

    /**
     * 构造函数，指定所有参数。
     * 
     * @param starTypeId 恒星类型标识
     * @param radiusInAU 恒星半径（AU）
     * @param minRadius 最小半径（AU，可选）
     * @param maxRadius 最大半径（AU，可选）
     */
    public StarSizeDefinition(String starTypeId, AstronomicalUnit radiusInAU,
                               AstronomicalUnit minRadius, AstronomicalUnit maxRadius) {
        this.starTypeId = starTypeId;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        setRadiusInAU(radiusInAU);
    }

    /**
     * 获取恒星类型标识。
     * 
     * @return 恒星类型标识
     */
    public String getStarTypeId() {
        return starTypeId;
    }

    /**
     * 设置恒星类型标识。
     * 
     * @param starTypeId 恒星类型标识
     */
    public void setStarTypeId(String starTypeId) {
        this.starTypeId = starTypeId;
    }

    /**
     * 获取恒星半径（AU）。
     * 
     * @return 恒星半径（AstronomicalUnit）
     */
    public AstronomicalUnit getRadiusInAU() {
        return radiusInAU;
    }

    /**
     * 设置恒星半径。
     * 
     * @param radius 恒星半径（AU）
     * @throws IllegalArgumentException 如果半径为 null、<= 0 或超出范围
     */
    public void setRadiusInAU(AstronomicalUnit radius) {
        if (radius == null) {
            throw new IllegalArgumentException("恒星半径不能为空");
        }
        if (radius.toAU() <= 0.0) {
            throw new IllegalArgumentException("恒星半径必须 > 0，当前值: " + radius.toAU() + " AU");
        }
        
        // 验证范围
        if (minRadius != null && radius.toAU() < minRadius.toAU()) {
            throw new IllegalArgumentException(
                "恒星半径小于最小值: " + radius.toAU() + " AU < " + minRadius.toAU() + " AU");
        }
        if (maxRadius != null && radius.toAU() > maxRadius.toAU()) {
            throw new IllegalArgumentException(
                "恒星半径大于最大值: " + radius.toAU() + " AU > " + maxRadius.toAU() + " AU");
        }
        
        this.radiusInAU = radius;
    }

    /**
     * 获取最小半径。
     * 
     * @return 最小半径（AstronomicalUnit）
     */
    public AstronomicalUnit getMinRadius() {
        return minRadius;
    }

    /**
     * 设置最小半径。
     * 
     * @param minRadius 最小半径（AU）
     */
    public void setMinRadius(AstronomicalUnit minRadius) {
        this.minRadius = minRadius;
    }

    /**
     * 获取最大半径。
     * 
     * @return 最大半径（AstronomicalUnit）
     */
    public AstronomicalUnit getMaxRadius() {
        return maxRadius;
    }

    /**
     * 设置最大半径。
     * 
     * @param maxRadius 最大半径（AU）
     */
    public void setMaxRadius(AstronomicalUnit maxRadius) {
        this.maxRadius = maxRadius;
    }

    /**
     * 从配置加载恒星大小定义（静态工厂方法）。
     * 
     * @param starTypeId 恒星类型标识
     * @return 恒星大小定义
     * @throws IllegalArgumentException 如果类型不存在或配置无效
     */
    public static StarSizeDefinition loadFromConfig(String starTypeId) {
        // 这个方法将由 SizePresetLoader 实现
        // 这里先抛出异常，提示需要使用 SizePresetLoader
        throw new UnsupportedOperationException(
            "请使用 SizePresetLoader.loadStarSizeDefinition() 方法从配置加载");
    }

    /**
     * 验证恒星大小的合理性。
     * 
     * @throws IllegalArgumentException 如果恒星大小不合理
     */
    public void validate() throws IllegalArgumentException {
        if (starTypeId == null || starTypeId.trim().isEmpty()) {
            throw new IllegalArgumentException("恒星类型标识不能为空");
        }
        
        if (radiusInAU == null) {
            throw new IllegalArgumentException("恒星半径不能为空");
        }
        
        if (radiusInAU.toAU() <= 0.0) {
            throw new IllegalArgumentException("恒星半径必须 > 0，当前值: " + radiusInAU.toAU() + " AU");
        }
        
        // 验证范围
        if (minRadius != null && radiusInAU.toAU() < minRadius.toAU()) {
            throw new IllegalArgumentException(
                "恒星半径小于最小值: " + radiusInAU.toAU() + " AU < " + minRadius.toAU() + " AU");
        }
        if (maxRadius != null && radiusInAU.toAU() > maxRadius.toAU()) {
            throw new IllegalArgumentException(
                "恒星半径大于最大值: " + radiusInAU.toAU() + " AU > " + maxRadius.toAU() + " AU");
        }
        
        // 验证符合真实恒星的物理特性
        // 例如：太阳半径 ≈ 0.00465 AU，红巨星半径可达 1-10 AU
        double radius = radiusInAU.toAU();
        if (radius < 0.00001 || radius > 100.0) {
            LOGGER.warning("恒星半径超出常见范围: " + radius + " AU（常见范围：0.00001 - 100 AU）");
        }
    }

    private static final java.util.logging.Logger LOGGER = 
        java.util.logging.Logger.getLogger(StarSizeDefinition.class.getName());

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StarSizeDefinition other = (StarSizeDefinition) obj;
        return (starTypeId == null ? other.starTypeId == null : starTypeId.equals(other.starTypeId)) &&
               radiusInAU.equals(other.radiusInAU) &&
               (minRadius == null ? other.minRadius == null : minRadius.equals(other.minRadius)) &&
               (maxRadius == null ? other.maxRadius == null : maxRadius.equals(other.maxRadius));
    }

    @Override
    public int hashCode() {
        int result = starTypeId != null ? starTypeId.hashCode() : 0;
        result = 31 * result + radiusInAU.hashCode();
        result = 31 * result + (minRadius != null ? minRadius.hashCode() : 0);
        result = 31 * result + (maxRadius != null ? maxRadius.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "StarSizeDefinition{starTypeId='" + starTypeId + 
               "', radiusInAU=" + radiusInAU.toAU() + 
               " AU, minRadius=" + (minRadius != null ? minRadius.toAU() : "null") + 
               " AU, maxRadius=" + (maxRadius != null ? maxRadius.toAU() : "null") + " AU}";
    }
}
