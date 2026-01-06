package com.staraxis.game.shared.world.astronomical;

import java.io.Serializable;

/**
 * 行星大小定义（Planet Size Definition）。
 * 
 * 作用（Purpose）：定义行星半径的大小，基于天文单位，按类型分类。
 * 实现方式：使用 AstronomicalUnit 表示行星半径，支持按类型加载配置，支持范围验证。
 * 
 * 依赖（Dependencies）：AstronomicalUnit。
 * 对外接口（Public API）：getRadiusInAU(), setRadiusInAU(), loadFromConfig(),
 * validate()。
 */
public class PlanetSizeDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 行星类型标识（如 "rocky", "gas_giant"）。
     */
    private String planetTypeId;

    /**
     * 行星半径（以 AU 为单位）。
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
    public PlanetSizeDefinition() {
    }

    /**
     * 构造函数，指定行星类型和半径。
     * 
     * @param planetTypeId 行星类型标识
     * @param radiusInAU   行星半径（AU）
     */
    public PlanetSizeDefinition(String planetTypeId, AstronomicalUnit radiusInAU) {
        this.planetTypeId = planetTypeId;
        setRadiusInAU(radiusInAU);
    }

    /**
     * 构造函数，指定所有参数。
     * 
     * @param planetTypeId 行星类型标识
     * @param radiusInAU   行星半径（AU）
     * @param minRadius    最小半径（AU，可选）
     * @param maxRadius    最大半径（AU，可选）
     */
    public PlanetSizeDefinition(String planetTypeId, AstronomicalUnit radiusInAU,
            AstronomicalUnit minRadius, AstronomicalUnit maxRadius) {
        this.planetTypeId = planetTypeId;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        setRadiusInAU(radiusInAU);
    }

    /**
     * 获取行星类型标识。
     * 
     * @return 行星类型标识
     */
    public String getPlanetTypeId() {
        return planetTypeId;
    }

    /**
     * 设置行星类型标识。
     * 
     * @param planetTypeId 行星类型标识
     */
    public void setPlanetTypeId(String planetTypeId) {
        this.planetTypeId = planetTypeId;
    }

    /**
     * 获取行星半径（AU）。
     * 
     * @return 行星半径（AstronomicalUnit）
     */
    public AstronomicalUnit getRadiusInAU() {
        return radiusInAU;
    }

    /**
     * 设置行星半径。
     * 
     * @param radius 行星半径（AU）
     * @throws IllegalArgumentException 如果半径为 null、<= 0 或超出范围
     */
    public void setRadiusInAU(AstronomicalUnit radius) {
        if (radius == null) {
            throw new IllegalArgumentException("行星半径不能为空");
        }
        if (radius.toAU() <= 0.0) {
            throw new IllegalArgumentException("行星半径必须 > 0，当前值: " + radius.toAU() + " AU");
        }

        // 验证范围
        if (minRadius != null && radius.toAU() < minRadius.toAU()) {
            throw new IllegalArgumentException(
                    "行星半径小于最小值: " + radius.toAU() + " AU < " + minRadius.toAU() + " AU");
        }
        if (maxRadius != null && radius.toAU() > maxRadius.toAU()) {
            throw new IllegalArgumentException(
                    "行星半径大于最大值: " + radius.toAU() + " AU > " + maxRadius.toAU() + " AU");
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
     * 从配置加载行星大小定义（静态工厂方法）。
     * 
     * @param planetTypeId 行星类型标识
     * @return 行星大小定义
     * @throws IllegalArgumentException 如果类型不存在或配置无效
     */
    public static PlanetSizeDefinition loadFromConfig(String planetTypeId) {
        // 这个方法将由 SizePresetLoader 实现
        // 这里先抛出异常，提示需要使用 SizePresetLoader
        throw new UnsupportedOperationException(
                "请使用 SizePresetLoader.loadPlanetSizeDefinition() 方法从配置加载");
    }

    /**
     * 验证行星大小的合理性。
     * 
     * @throws IllegalArgumentException 如果行星大小不合理
     */
    public void validate() throws IllegalArgumentException {
        if (planetTypeId == null || planetTypeId.trim().isEmpty()) {
            throw new IllegalArgumentException("行星类型标识不能为空");
        }

        if (radiusInAU == null) {
            throw new IllegalArgumentException("行星半径不能为空");
        }

        if (radiusInAU.toAU() <= 0.0) {
            throw new IllegalArgumentException("行星半径必须 > 0，当前值: " + radiusInAU.toAU() + " AU");
        }

        // 验证范围
        if (minRadius != null && radiusInAU.toAU() < minRadius.toAU()) {
            throw new IllegalArgumentException(
                    "行星半径小于最小值: " + radiusInAU.toAU() + " AU < " + minRadius.toAU() + " AU");
        }
        if (maxRadius != null && radiusInAU.toAU() > maxRadius.toAU()) {
            throw new IllegalArgumentException(
                    "行星半径大于最大值: " + radiusInAU.toAU() + " AU > " + maxRadius.toAU() + " AU");
        }

        // 验证符合真实行星的物理特性
        // 例如：地球半径 ≈ 0.0000426 AU，木星半径 ≈ 0.000477 AU
        double radius = radiusInAU.toAU();
        if (radius < 0.000001 || radius > 0.01) {
            LOGGER.warning("行星半径超出常见范围: " + radius + " AU（常见范围：0.000001 - 0.01 AU）");
        }
    }

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger
            .getLogger(PlanetSizeDefinition.class.getName());

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PlanetSizeDefinition other = (PlanetSizeDefinition) obj;
        return (planetTypeId == null ? other.planetTypeId == null : planetTypeId.equals(other.planetTypeId)) &&
                radiusInAU.equals(other.radiusInAU) &&
                (minRadius == null ? other.minRadius == null : minRadius.equals(other.minRadius)) &&
                (maxRadius == null ? other.maxRadius == null : maxRadius.equals(other.maxRadius));
    }

    @Override
    public int hashCode() {
        int result = planetTypeId != null ? planetTypeId.hashCode() : 0;
        result = 31 * result + radiusInAU.hashCode();
        result = 31 * result + (minRadius != null ? minRadius.hashCode() : 0);
        result = 31 * result + (maxRadius != null ? maxRadius.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PlanetSizeDefinition{planetTypeId='" + planetTypeId +
                "', radiusInAU=" + radiusInAU.toAU() +
                " AU, minRadius=" + (minRadius != null ? minRadius.toAU() : "null") +
                " AU, maxRadius=" + (maxRadius != null ? maxRadius.toAU() : "null") + " AU}";
    }
}
