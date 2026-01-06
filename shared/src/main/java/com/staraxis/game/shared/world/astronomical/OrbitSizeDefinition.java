package com.staraxis.game.shared.world.astronomical;

import java.io.Serializable;

/**
 * 轨道大小定义（Orbit Size Definition）。
 * 
 * 作用（Purpose）：定义轨道半长轴的大小，基于天文单位，符合真实行星系统比例。
 * 实现方式：使用 AstronomicalUnit 表示轨道半长轴，支持最小值和最大值范围验证。
 * 
 * 依赖（Dependencies）：AstronomicalUnit。
 * 对外接口（Public API）：getSemiMajorAxis(), setSemiMajorAxis(), validate()。
 */
public class OrbitSizeDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 轨道半长轴（以 AU 为单位）。
     * 必须 > 0，且在 [minValue, maxValue] 范围内。
     */
    private AstronomicalUnit semiMajorAxis;

    /**
     * 最小轨道半长轴（可选，默认 0.1 AU）。
     */
    private AstronomicalUnit minValue;

    /**
     * 最大轨道半长轴（可选，默认 100 AU）。
     */
    private AstronomicalUnit maxValue;

    /**
     * 默认构造函数，使用默认值。
     */
    public OrbitSizeDefinition() {
        // 默认值：1 AU（地球轨道）
        this.semiMajorAxis = AstronomicalUnit.fromAU(1.0);
        this.minValue = AstronomicalUnit.fromAU(0.1);
        this.maxValue = AstronomicalUnit.fromAU(100.0);
    }

    /**
     * 构造函数，指定轨道半长轴。
     * 
     * @param semiMajorAxis 轨道半长轴（AU）
     */
    public OrbitSizeDefinition(AstronomicalUnit semiMajorAxis) {
        this();
        setSemiMajorAxis(semiMajorAxis);
    }

    /**
     * 构造函数，指定轨道半长轴和范围。
     * 
     * @param semiMajorAxis 轨道半长轴（AU）
     * @param minValue 最小轨道半长轴（AU）
     * @param maxValue 最大轨道半长轴（AU）
     */
    public OrbitSizeDefinition(AstronomicalUnit semiMajorAxis, 
                                AstronomicalUnit minValue, 
                                AstronomicalUnit maxValue) {
        setMinValue(minValue);
        setMaxValue(maxValue);
        setSemiMajorAxis(semiMajorAxis);
    }

    /**
     * 获取轨道半长轴。
     * 
     * @return 轨道半长轴（AstronomicalUnit）
     */
    public AstronomicalUnit getSemiMajorAxis() {
        return semiMajorAxis;
    }

    /**
     * 设置轨道半长轴。
     * 
     * @param axis 轨道半长轴（AU）
     * @throws IllegalArgumentException 如果轴为 null、<= 0 或超出范围
     */
    public void setSemiMajorAxis(AstronomicalUnit axis) {
        if (axis == null) {
            throw new IllegalArgumentException("轨道半长轴不能为空");
        }
        if (axis.toAU() <= 0.0) {
            throw new IllegalArgumentException("轨道半长轴必须 > 0，当前值: " + axis.toAU() + " AU");
        }
        
        // 验证范围
        if (minValue != null && axis.toAU() < minValue.toAU()) {
            throw new IllegalArgumentException(
                "轨道半长轴小于最小值: " + axis.toAU() + " AU < " + minValue.toAU() + " AU");
        }
        if (maxValue != null && axis.toAU() > maxValue.toAU()) {
            throw new IllegalArgumentException(
                "轨道半长轴大于最大值: " + axis.toAU() + " AU > " + maxValue.toAU() + " AU");
        }
        
        this.semiMajorAxis = axis;
    }

    /**
     * 获取最小轨道半长轴。
     * 
     * @return 最小轨道半长轴（AstronomicalUnit）
     */
    public AstronomicalUnit getMinValue() {
        return minValue;
    }

    /**
     * 设置最小轨道半长轴。
     * 
     * @param minValue 最小轨道半长轴（AU）
     * @throws IllegalArgumentException 如果最小值为 null 或 <= 0
     */
    public void setMinValue(AstronomicalUnit minValue) {
        if (minValue == null) {
            throw new IllegalArgumentException("最小轨道半长轴不能为空");
        }
        if (minValue.toAU() <= 0.0) {
            throw new IllegalArgumentException("最小轨道半长轴必须 > 0，当前值: " + minValue.toAU() + " AU");
        }
        this.minValue = minValue;
    }

    /**
     * 获取最大轨道半长轴。
     * 
     * @return 最大轨道半长轴（AstronomicalUnit）
     */
    public AstronomicalUnit getMaxValue() {
        return maxValue;
    }

    /**
     * 设置最大轨道半长轴。
     * 
     * @param maxValue 最大轨道半长轴（AU）
     * @throws IllegalArgumentException 如果最大值为 null 或 <= 0，或小于最小值
     */
    public void setMaxValue(AstronomicalUnit maxValue) {
        if (maxValue == null) {
            throw new IllegalArgumentException("最大轨道半长轴不能为空");
        }
        if (maxValue.toAU() <= 0.0) {
            throw new IllegalArgumentException("最大轨道半长轴必须 > 0，当前值: " + maxValue.toAU() + " AU");
        }
        if (minValue != null && maxValue.toAU() < minValue.toAU()) {
            throw new IllegalArgumentException(
                "最大轨道半长轴必须 >= 最小值: " + maxValue.toAU() + " AU < " + minValue.toAU() + " AU");
        }
        this.maxValue = maxValue;
    }

    /**
     * 验证轨道大小的合理性。
     * 
     * @throws IllegalArgumentException 如果轨道大小不合理
     */
    public void validate() throws IllegalArgumentException {
        if (semiMajorAxis == null) {
            throw new IllegalArgumentException("轨道半长轴不能为空");
        }
        
        if (semiMajorAxis.toAU() <= 0.0) {
            throw new IllegalArgumentException("轨道半长轴必须 > 0，当前值: " + semiMajorAxis.toAU() + " AU");
        }
        
        // 验证范围
        if (minValue != null && semiMajorAxis.toAU() < minValue.toAU()) {
            throw new IllegalArgumentException(
                "轨道半长轴小于最小值: " + semiMajorAxis.toAU() + " AU < " + minValue.toAU() + " AU");
        }
        if (maxValue != null && semiMajorAxis.toAU() > maxValue.toAU()) {
            throw new IllegalArgumentException(
                "轨道半长轴大于最大值: " + semiMajorAxis.toAU() + " AU > " + maxValue.toAU() + " AU");
        }
        
        // 验证符合真实行星系统的比例关系
        // 例如：地球轨道 = 1 AU，木星轨道 ≈ 5.2 AU
        double au = semiMajorAxis.toAU();
        if (au < 0.01 || au > 1000.0) {
            LOGGER.warning("轨道半长轴超出常见行星系统范围: " + au + " AU（常见范围：0.01 - 1000 AU）");
        }
    }

    private static final java.util.logging.Logger LOGGER = 
        java.util.logging.Logger.getLogger(OrbitSizeDefinition.class.getName());

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        OrbitSizeDefinition other = (OrbitSizeDefinition) obj;
        return semiMajorAxis.equals(other.semiMajorAxis) &&
               (minValue == null ? other.minValue == null : minValue.equals(other.minValue)) &&
               (maxValue == null ? other.maxValue == null : maxValue.equals(other.maxValue));
    }

    @Override
    public int hashCode() {
        int result = semiMajorAxis.hashCode();
        result = 31 * result + (minValue != null ? minValue.hashCode() : 0);
        result = 31 * result + (maxValue != null ? maxValue.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OrbitSizeDefinition{semiMajorAxis=" + semiMajorAxis.toAU() + 
               " AU, minValue=" + (minValue != null ? minValue.toAU() : "null") + 
               " AU, maxValue=" + (maxValue != null ? maxValue.toAU() : "null") + " AU}";
    }
}
