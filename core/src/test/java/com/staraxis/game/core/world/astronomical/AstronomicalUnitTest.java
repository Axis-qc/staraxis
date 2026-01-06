package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.ArithmeticOverflowException;
import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;

/**
 * AstronomicalUnit 测试类。
 * 
 * 作用（Purpose）：测试 AstronomicalUnit 类的所有功能，包括工厂方法、转换方法、算术运算和溢出检查。
 */
class AstronomicalUnitTest {

    // ========== 工厂方法测试 ==========

    @Test
    void testFromAU_ValidValue() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
        assertNotNull(au);
        assertEquals(1.0, au.toAU(), 1e-10);
    }

    @Test
    void testFromAU_Zero() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(0.0);
        assertNotNull(au);
        assertEquals(0.0, au.toAU(), 1e-10);
    }

    @Test
    void testFromAU_NegativeValue() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(-1.0);
        assertNotNull(au);
        assertEquals(-1.0, au.toAU(), 1e-10);
    }

    @Test
    void testFromAU_InvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            AstronomicalUnit.fromAU(Double.NaN);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            AstronomicalUnit.fromAU(Double.POSITIVE_INFINITY);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            AstronomicalUnit.fromAU(Double.NEGATIVE_INFINITY);
        });
    }

    @Test
    void testFromLightYears_ValidValue() {
        AstronomicalUnit au = AstronomicalUnit.fromLightYears(1.0);
        assertNotNull(au);
        // 1 光年 = 63,241.077 AU
        assertEquals(63_241.077, au.toAU(), 1e-3);
    }

    @Test
    void testFromParsecs_ValidValue() {
        AstronomicalUnit au = AstronomicalUnit.fromParsecs(1.0);
        assertNotNull(au);
        // 1 秒差距 = 206,265 AU
        assertEquals(206_265.0, au.toAU(), 1e-3);
    }

    @Test
    void testFromInternalUnits() {
        long internalUnits = 1_000_000_000_000L; // 1 AU
        AstronomicalUnit au = AstronomicalUnit.fromInternalUnits(internalUnits);
        assertNotNull(au);
        assertEquals(1.0, au.toAU(), 1e-10);
    }

    // ========== 转换方法测试 ==========

    @Test
    void testToAU() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
        assertEquals(1.0, au.toAU(), 1e-10);
    }

    @Test
    void testToLightYears() {
        AstronomicalUnit au = AstronomicalUnit.fromLightYears(1.0);
        assertEquals(1.0, au.toLightYears(), 1e-6);
    }

    @Test
    void testToParsecs() {
        AstronomicalUnit au = AstronomicalUnit.fromParsecs(1.0);
        assertEquals(1.0, au.toParsecs(), 1e-6);
    }

    @Test
    void testConversionPrecision() {
        // 测试转换精度：1 AU 转换为光年再转回 AU 应该接近原始值
        AstronomicalUnit original = AstronomicalUnit.fromAU(1.0);
        double ly = original.toLightYears();
        AstronomicalUnit converted = AstronomicalUnit.fromLightYears(ly);
        
        // 精度应该达到 99.9% 以上
        double precision = Math.abs(1.0 - converted.toAU()) / 1.0;
        assertTrue(precision < 0.001, "转换精度应该达到 99.9% 以上，实际精度: " + (1 - precision) * 100 + "%");
    }

    // ========== 算术运算测试 ==========

    @Test
    void testAdd() {
        AstronomicalUnit au1 = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit au2 = AstronomicalUnit.fromAU(2.0);
        AstronomicalUnit result = au1.add(au2);
        
        assertEquals(3.0, result.toAU(), 1e-10);
    }

    @Test
    void testSubtract() {
        AstronomicalUnit au1 = AstronomicalUnit.fromAU(3.0);
        AstronomicalUnit au2 = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit result = au1.subtract(au2);
        
        assertEquals(2.0, result.toAU(), 1e-10);
    }

    @Test
    void testMultiply() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(2.0);
        AstronomicalUnit result = au.multiply(3);
        
        assertEquals(6.0, result.toAU(), 1e-10);
    }

    @Test
    void testDivide() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(6.0);
        AstronomicalUnit result = au.divide(3);
        
        assertEquals(2.0, result.toAU(), 1e-10);
    }

    @Test
    void testDivide_ByZero() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
        assertThrows(IllegalArgumentException.class, () -> {
            au.divide(0);
        });
    }

    // ========== 溢出检查测试 ==========

    @Test
    void testOverflow_Add() {
        // 测试接近最大值但不溢出的情况
        double maxSafeValue = Long.MAX_VALUE / (double) 1_000_000_000_000L / 2.0;
        AstronomicalUnit max = AstronomicalUnit.fromAU(maxSafeValue);
        AstronomicalUnit large = AstronomicalUnit.fromAU(1.0);
        
        // 如果值足够小，加法不应该溢出
        assertDoesNotThrow(() -> {
            max.add(large);
        });
        
        // 测试真正溢出的情况
        AstronomicalUnit veryLarge = AstronomicalUnit.fromAU(maxSafeValue);
        assertThrows(ArithmeticOverflowException.class, () -> {
            veryLarge.add(veryLarge);
        });
    }

    @Test
    void testOverflow_Multiply() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(Long.MAX_VALUE / (double) 1_000_000_000_000L / 2.0);
        
        // 乘以一个大的因子可能导致溢出
        assertThrows(ArithmeticOverflowException.class, () -> {
            au.multiply(Long.MAX_VALUE);
        });
    }

    // ========== 确定性测试 ==========

    @Test
    void testDeterminism_SameInputSameOutput() {
        // 相同输入应该产生相同输出
        AstronomicalUnit au1 = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit au2 = AstronomicalUnit.fromAU(1.0);
        
        assertEquals(au1.getInternalUnits(), au2.getInternalUnits());
        assertEquals(au1, au2);
    }

    @Test
    void testDeterminism_ArithmeticOperations() {
        // 算术运算应该是确定性的
        AstronomicalUnit au1 = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit au2 = AstronomicalUnit.fromAU(2.0);
        
        AstronomicalUnit result1 = au1.add(au2);
        AstronomicalUnit result2 = au1.add(au2);
        
        assertEquals(result1.getInternalUnits(), result2.getInternalUnits());
        assertEquals(result1, result2);
    }

    // ========== equals 和 hashCode 测试 ==========

    @Test
    void testEquals() {
        AstronomicalUnit au1 = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit au2 = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit au3 = AstronomicalUnit.fromAU(2.0);
        
        assertEquals(au1, au2);
        assertNotEquals(au1, au3);
        assertNotEquals(au1, null);
        assertNotEquals(au1, "not an AstronomicalUnit");
    }

    @Test
    void testHashCode() {
        AstronomicalUnit au1 = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit au2 = AstronomicalUnit.fromAU(1.0);
        
        assertEquals(au1.hashCode(), au2.hashCode());
    }
}
