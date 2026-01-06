package com.staraxis.game.shared.world.astronomical;

import java.io.Serializable;

/**
 * 天文单位（Astronomical Unit, AU）基础类。
 * 
 * 作用（Purpose）：使用定点数表示天文单位值，确保确定性和高性能。
 * 实现方式：使用 long 类型存储内部单位值，缩放因子为 10^12（1 AU = 10^12 内部单位）。
 * 
 * 依赖（Dependencies）：仅 Java 标准库。
 * 对外接口（Public API）：工厂方法（fromAU, fromLightYears, fromParsecs）、
 * 转换方法（toAU, toLightYears, toParsecs）、算术运算方法（add, subtract, multiply, divide）。
 */
public class AstronomicalUnit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 内部单位值（1 AU = 10^12 内部单位）。
     */
    private final long internalUnits;

    /**
     * 缩放因子：1 AU = 10^12 内部单位。
     * 使用 10^12 可以精确表示 AU 级别的小数，同时保持 long 类型的范围。
     */
    private static final long SCALE_FACTOR = 1_000_000_000_000L; // 10^12

    /**
     * 私有构造函数，使用工厂方法创建实例。
     * 
     * @param internalUnits 内部单位值
     */
    private AstronomicalUnit(long internalUnits) {
        this.internalUnits = internalUnits;
    }

    /**
     * 从 AU 值创建 AstronomicalUnit 实例。
     * 
     * @param auValue AU 值
     * @return AstronomicalUnit 实例
     * @throws ArithmeticException 如果转换后超出 long 类型范围
     */
    public static AstronomicalUnit fromAU(double auValue) {
        if (!Double.isFinite(auValue)) {
            throw new IllegalArgumentException("AU 值必须为有限数值");
        }
        
        // 转换为内部单位：internalUnits = auValue * SCALE_FACTOR
        // 检查溢出：如果 auValue * SCALE_FACTOR 超出 long 范围，则抛出异常
        if (Math.abs(auValue) > (Long.MAX_VALUE / (double) SCALE_FACTOR)) {
            throw new ArithmeticOverflowException(
                "AU 值 " + auValue + " 超出可表示范围（最大约 ±" + 
                (Long.MAX_VALUE / (double) SCALE_FACTOR) + " AU）");
        }
        
        long internal = Math.round(auValue * SCALE_FACTOR);
        return new AstronomicalUnit(internal);
    }

    /**
     * 从光年值创建 AstronomicalUnit 实例。
     * 转换关系：1 光年 = 63,241.077 AU
     * 
     * @param lyValue 光年值
     * @return AstronomicalUnit 实例
     * @throws ArithmeticException 如果转换后超出 long 类型范围
     */
    public static AstronomicalUnit fromLightYears(double lyValue) {
        if (!Double.isFinite(lyValue)) {
            throw new IllegalArgumentException("光年值必须为有限数值");
        }
        
        // 先转换为 AU，再创建实例
        double auValue = lyValue * UnitConverter.LY_TO_AU;
        return fromAU(auValue);
    }

    /**
     * 从秒差距值创建 AstronomicalUnit 实例。
     * 转换关系：1 秒差距 = 206,265 AU
     * 
     * @param pcValue 秒差距值
     * @return AstronomicalUnit 实例
     * @throws ArithmeticException 如果转换后超出 long 类型范围
     */
    public static AstronomicalUnit fromParsecs(double pcValue) {
        if (!Double.isFinite(pcValue)) {
            throw new IllegalArgumentException("秒差距值必须为有限数值");
        }
        
        // 先转换为 AU，再创建实例
        double auValue = pcValue * UnitConverter.PC_TO_AU;
        return fromAU(auValue);
    }

    /**
     * 从内部单位值创建 AstronomicalUnit 实例（内部使用）。
     * 
     * @param internalUnits 内部单位值
     * @return AstronomicalUnit 实例
     */
    public static AstronomicalUnit fromInternalUnits(long internalUnits) {
        return new AstronomicalUnit(internalUnits);
    }

    /**
     * 转换为 AU 值。
     * 
     * @return AU 值（double）
     */
    public double toAU() {
        return internalUnits / (double) SCALE_FACTOR;
    }

    /**
     * 转换为光年值。
     * 转换关系：1 光年 = 63,241.077 AU
     * 
     * @return 光年值（double）
     */
    public double toLightYears() {
        return toAU() / UnitConverter.LY_TO_AU;
    }

    /**
     * 转换为秒差距值。
     * 转换关系：1 秒差距 = 206,265 AU
     * 
     * @return 秒差距值（double）
     */
    public double toParsecs() {
        return toAU() / UnitConverter.PC_TO_AU;
    }

    /**
     * 获取内部单位值（内部使用）。
     * 
     * @return 内部单位值
     */
    public long getInternalUnits() {
        return internalUnits;
    }

    /**
     * 加法运算。
     * 
     * @param other 另一个 AstronomicalUnit 实例
     * @return 运算结果
     * @throws ArithmeticOverflowException 如果运算结果溢出
     */
    public AstronomicalUnit add(AstronomicalUnit other) {
        if (other == null) {
            throw new IllegalArgumentException("另一个天文单位不能为空");
        }
        
        // 检查溢出
        long result = internalUnits + other.internalUnits;
        if ((internalUnits > 0 && other.internalUnits > 0 && result < 0) ||
            (internalUnits < 0 && other.internalUnits < 0 && result > 0)) {
            throw new ArithmeticOverflowException("加法运算溢出");
        }
        
        return new AstronomicalUnit(result);
    }

    /**
     * 减法运算。
     * 
     * @param other 另一个 AstronomicalUnit 实例
     * @return 运算结果
     * @throws ArithmeticOverflowException 如果运算结果溢出
     */
    public AstronomicalUnit subtract(AstronomicalUnit other) {
        if (other == null) {
            throw new IllegalArgumentException("另一个天文单位不能为空");
        }
        
        // 检查溢出
        long result = internalUnits - other.internalUnits;
        if ((internalUnits > 0 && other.internalUnits < 0 && result < 0) ||
            (internalUnits < 0 && other.internalUnits > 0 && result > 0)) {
            throw new ArithmeticOverflowException("减法运算溢出");
        }
        
        return new AstronomicalUnit(result);
    }

    /**
     * 乘法运算（整数因子）。
     * 
     * @param factor 整数因子
     * @return 运算结果
     * @throws ArithmeticOverflowException 如果运算结果溢出
     */
    public AstronomicalUnit multiply(long factor) {
        // 检查溢出：如果 internalUnits * factor 超出 long 范围
        if (factor != 0 && 
            Math.abs(internalUnits) > Math.abs(Long.MAX_VALUE / factor)) {
            throw new ArithmeticOverflowException("乘法运算溢出");
        }
        
        return new AstronomicalUnit(internalUnits * factor);
    }

    /**
     * 除法运算（整数除数）。
     * 
     * @param divisor 整数除数
     * @return 运算结果
     * @throws IllegalArgumentException 如果除数为 0
     */
    public AstronomicalUnit divide(long divisor) {
        if (divisor == 0) {
            throw new IllegalArgumentException("除数不能为 0");
        }
        
        return new AstronomicalUnit(internalUnits / divisor);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AstronomicalUnit other = (AstronomicalUnit) obj;
        return internalUnits == other.internalUnits;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(internalUnits);
    }

    @Override
    public String toString() {
        return "AstronomicalUnit{internalUnits=" + internalUnits + 
               ", au=" + toAU() + "}";
    }
}
