package com.staraxis.game.shared.world.astronomical;

import java.io.Serializable;

/**
 * 星区大小定义（Sector Size Definition）。
 * 
 * 作用（Purpose）：定义星区的大小，基于天文单位。
 * 实现方式：使用 AstronomicalUnit 表示星区大小，默认 1 光年 = 63,241 AU。
 * 
 * 依赖（Dependencies）：AstronomicalUnit, UnitConverter。
 * 对外接口（Public API）：getSizeInAU(), getSizeInLightYears(), setSizeInAU(), validate()。
 */
public class SectorSizeDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 星区大小（以 AU 为单位）。
     * 默认值：1 光年 = 63,241.077 AU
     */
    private AstronomicalUnit sizeInAU;

    /**
     * 是否可配置。
     * 如果为 true，允许通过配置文件修改星区大小；如果为 false，使用默认值。
     */
    private boolean isConfigurable;

    /**
     * 默认构造函数，使用默认值（1 光年）。
     */
    public SectorSizeDefinition() {
        // 默认 1 光年 = 63,241.077 AU
        this.sizeInAU = AstronomicalUnit.fromLightYears(1.0);
        this.isConfigurable = true;
    }

    /**
     * 构造函数，指定星区大小。
     * 
     * @param sizeInAU 星区大小（AU）
     * @param isConfigurable 是否可配置
     */
    public SectorSizeDefinition(AstronomicalUnit sizeInAU, boolean isConfigurable) {
        setSizeInAU(sizeInAU);
        this.isConfigurable = isConfigurable;
    }

    /**
     * 获取星区大小（AU）。
     * 
     * @return 星区大小（AstronomicalUnit）
     */
    public AstronomicalUnit getSizeInAU() {
        return sizeInAU;
    }

    /**
     * 获取星区大小（光年）。
     * 
     * @return 星区大小（光年，double）
     */
    public double getSizeInLightYears() {
        return sizeInAU.toLightYears();
    }

    /**
     * 设置星区大小。
     * 
     * @param size 星区大小（AU）
     * @throws IllegalArgumentException 如果大小为 null 或 <= 0
     */
    public void setSizeInAU(AstronomicalUnit size) {
        if (size == null) {
            throw new IllegalArgumentException("星区大小不能为空");
        }
        if (size.toAU() <= 0.0) {
            throw new IllegalArgumentException("星区大小必须 > 0，当前值: " + size.toAU() + " AU");
        }
        this.sizeInAU = size;
    }

    /**
     * 是否可配置。
     * 
     * @return 是否可配置
     */
    public boolean isConfigurable() {
        return isConfigurable;
    }

    /**
     * 设置是否可配置。
     * 
     * @param configurable 是否可配置
     */
    public void setConfigurable(boolean configurable) {
        this.isConfigurable = configurable;
    }

    /**
     * 验证星区大小的合理性。
     * 
     * @throws IllegalArgumentException 如果星区大小不合理
     */
    public void validate() throws IllegalArgumentException {
        if (sizeInAU == null) {
            throw new IllegalArgumentException("星区大小不能为空");
        }
        
        if (sizeInAU.toAU() <= 0.0) {
            throw new IllegalArgumentException("星区大小必须 > 0，当前值: " + sizeInAU.toAU() + " AU");
        }
        
        // 验证星区大小能够合理容纳一个恒星系（建议 >= 1 光年）
        double sizeInLy = sizeInAU.toLightYears();
        if (sizeInLy < 0.1) {
            throw new IllegalArgumentException(
                "星区大小过小，无法合理容纳恒星系。建议 >= 1 光年，当前值: " + 
                sizeInLy + " 光年 (" + sizeInAU.toAU() + " AU)");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SectorSizeDefinition other = (SectorSizeDefinition) obj;
        return sizeInAU.equals(other.sizeInAU) && isConfigurable == other.isConfigurable;
    }

    @Override
    public int hashCode() {
        int result = sizeInAU.hashCode();
        result = 31 * result + Boolean.hashCode(isConfigurable);
        return result;
    }

    @Override
    public String toString() {
        return "SectorSizeDefinition{sizeInAU=" + sizeInAU.toAU() + 
               " AU, sizeInLightYears=" + sizeInAU.toLightYears() + 
               " ly, configurable=" + isConfigurable + "}";
    }
}
