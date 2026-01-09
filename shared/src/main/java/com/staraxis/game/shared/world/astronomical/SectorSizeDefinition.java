package com.staraxis.game.shared.world.astronomical;

import com.staraxis.game.shared.world.HexCoord;

/**
 * 星区尺寸定义，表示六边形星区的物理尺寸。
 * 
 * 默认配置：
 * - 星区半径：0.5 光年
 * - 星区直径：1.0 光年
 * - 1 光年 = 63,241 AU（天文单位）
 * 
 * 提供的接口：
 * - 获取星区半径/直径（光年/AU）
 * - 坐标转换：六边形坐标 <-> 物理世界坐标
 */
public class SectorSizeDefinition {
    
    // 1 光年 = 63,241 AU
    public static final double LIGHT_YEAR_IN_AU = 63_241.0;
    
    // 默认星区半径：0.5 光年
    public static final double DEFAULT_SECTOR_RADIUS_LY = 0.5;
    
    // 默认星区直径：1.0 光年
    public static final double DEFAULT_SECTOR_DIAMETER_LY = 1.0;
    
    // 六边形外接圆半径（世界单位，光年）
    private final double radiusInWorldUnits;
    
    // 六边形外接圆直径（世界单位，光年）
    private final double diameterInWorldUnits;
    
    // 是否可配置（用于兼容旧代码）
    private final boolean configurable;
    
    /**
     * 使用默认尺寸创建星区定义
     */
    public SectorSizeDefinition() {
        this(DEFAULT_SECTOR_RADIUS_LY);
    }
    
    /**
     * 使用指定半径（光年）创建星区定义
     * @param radiusInLightYears 星区半径（光年）
     */
    public SectorSizeDefinition(double radiusInLightYears) {
        this(radiusInLightYears, true);
    }
    
    /**
     * 使用 AstronomicalUnit 创建星区定义（兼容旧代码）
     * @param sizeInAU 星区大小（天文单位）
     * @param configurable 是否可配置
     */
    public SectorSizeDefinition(AstronomicalUnit sizeInAU, boolean configurable) {
        if (sizeInAU == null) {
            throw new IllegalArgumentException("星区大小不能为空");
        }
        // 将 AU 转换为光年
        double radiusInAU = sizeInAU.toAU();
        double radiusInLightYears = radiusInAU / LIGHT_YEAR_IN_AU;
        if (radiusInLightYears <= 0) {
            throw new IllegalArgumentException("星区半径必须大于零");
        }
        this.radiusInWorldUnits = radiusInLightYears;
        this.diameterInWorldUnits = radiusInLightYears * 2;
        this.configurable = configurable;
    }
    
    /**
     * 使用指定半径（光年）和可配置标志创建星区定义
     * @param radiusInLightYears 星区半径（光年）
     * @param configurable 是否可配置
     */
    private SectorSizeDefinition(double radiusInLightYears, boolean configurable) {
        if (radiusInLightYears <= 0) {
            throw new IllegalArgumentException("星区半径必须大于零");
        }
        this.radiusInWorldUnits = radiusInLightYears;
        this.diameterInWorldUnits = radiusInLightYears * 2;
        this.configurable = configurable;
    }
    
    // ===== 基础属性 =====
    
    /**
     * 获取星区半径（光年）
     */
    public double getRadiusInLightYears() {
        return radiusInWorldUnits;
    }
    
    /**
     * 获取星区直径（光年）
     */
    public double getDiameterInLightYears() {
        return diameterInWorldUnits;
    }
    
    /**
     * 获取星区半径（AU）
     */
    public double getRadiusInAU() {
        return radiusInWorldUnits * LIGHT_YEAR_IN_AU;
    }
    
    /**
     * 获取星区直径（AU）
     */
    public double getDiameterInAU() {
        return diameterInWorldUnits * LIGHT_YEAR_IN_AU;
    }
    
    /**
     * 获取六边形外接圆半径（世界单位）
     */
    public double getRadiusInWorldUnits() {
        return radiusInWorldUnits;
    }
    
    /**
     * 获取六边形外接圆直径（世界单位）
     */
    public double getDiameterInWorldUnits() {
        return diameterInWorldUnits;
    }
    
    /**
     * 获取星区大小（天文单位）- 兼容旧代码
     * @return 星区大小（天文单位）
     */
    public AstronomicalUnit getSizeInAU() {
        return AstronomicalUnit.fromAU(getDiameterInAU());
    }
    
    /**
     * 验证星区大小定义的合理性 - 兼容旧代码
     * @throws IllegalArgumentException 如果配置不合理
     */
    public void validate() throws IllegalArgumentException {
        if (radiusInWorldUnits <= 0) {
            throw new IllegalArgumentException("星区半径必须大于零，当前值: " + radiusInWorldUnits);
        }
        if (diameterInWorldUnits <= 0) {
            throw new IllegalArgumentException("星区直径必须大于零，当前值: " + diameterInWorldUnits);
        }
        if (diameterInWorldUnits != radiusInWorldUnits * 2) {
            throw new IllegalArgumentException("星区直径必须等于半径的两倍");
        }
    }
    
    /**
     * 是否可配置
     * @return 是否可配置
     */
    public boolean isConfigurable() {
        return configurable;
    }
    
    // ===== 坐标转换 =====
    
    /**
     * 将六边形坐标转换为物理世界坐标（光年）
     * 
     * 使用立方体坐标（cubic coordinates）到世界坐标的标准转换公式。
     * 对于 pointy-top 六边形布局：
     * - x = sqrt(3) * (q + r/2) * radius
     * - y = 3/2 * r * radius
     * 
     * 其中 q = hexCoord.getX(), r = hexCoord.getY()
     * 
     * @param hexCoord 六边形坐标（立方体坐标系统）
     * @return 世界坐标 [x, y]（光年）
     */
    public double[] hexToWorld(HexCoord hexCoord) {
        // 立方体坐标：q = x, r = y, s = z (约束：q + r + s = 0)
        int q = hexCoord.getX();
        int r = hexCoord.getY();
        
        // Pointy-top 六边形布局的标准转换公式
        // size 是六边形的外接圆半径
        double sqrt3 = Math.sqrt(3.0);
        double x = sqrt3 * (q + r / 2.0) * radiusInWorldUnits;
        double y = 1.5 * r * radiusInWorldUnits;
        
        return new double[]{x, y};
    }
    
    /**
     * 将物理世界坐标（光年）转换为最近的六边形坐标
     * 
     * 使用世界坐标到立方体坐标的标准转换公式（pointy-top 布局的逆变换）。
     * 
     * @param worldX 世界X坐标（光年）
     * @param worldY 世界Y坐标（光年）
     * @return 最近的六边形坐标（立方体坐标系统）
     */
    public HexCoord worldToHex(double worldX, double worldY) {
        // Pointy-top 六边形布局的逆变换
        // 从世界坐标 (x, y) 转换为立方体坐标 (q, r, s)
        double sqrt3 = Math.sqrt(3.0);
        
        // 逆变换公式：
        // q = (sqrt(3)/3 * x - 1/3 * y) / radius
        // r = (2/3 * y) / radius
        double q = (sqrt3 / 3.0 * worldX - 1.0 / 3.0 * worldY) / radiusInWorldUnits;
        double r = (2.0 / 3.0 * worldY) / radiusInWorldUnits;
        double s = -q - r;
        
        // 四舍五入到最近的整数
        int qRounded = (int) Math.round(q);
        int rRounded = (int) Math.round(r);
        int sRounded = (int) Math.round(s);
        
        // 由于浮点误差，四舍五入后的坐标可能不满足 q + r + s = 0
        // 计算差值并调整
        int qDiff = qRounded - (int) Math.round(q);
        int rDiff = rRounded - (int) Math.round(r);
        int sDiff = sRounded - (int) Math.round(s);
        
        // 如果总和不为0，调整差值最大的坐标
        int sum = qRounded + rRounded + sRounded;
        if (sum != 0) {
            if (Math.abs(qDiff) >= Math.abs(rDiff) && Math.abs(qDiff) >= Math.abs(sDiff)) {
                qRounded = -rRounded - sRounded;
            } else if (Math.abs(rDiff) >= Math.abs(sDiff)) {
                rRounded = -qRounded - sRounded;
            } else {
                sRounded = -qRounded - rRounded;
            }
        }
        
        return HexCoord.of(qRounded, rRounded, sRounded);
    }
    
    @Override
    public String toString() {
        return String.format("SectorSize{radius=%.2f ly, diameter=%.2f ly}", 
            radiusInWorldUnits, diameterInWorldUnits);
    }
}