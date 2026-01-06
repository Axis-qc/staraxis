package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.UnitConverter;

/**
 * UnitConverter 测试类。
 * 
 * 作用（Purpose）：测试 UnitConverter 类的所有转换方法，验证转换精度达到 99.9% 以上。
 */
class UnitConverterTest {

    // ========== AU 转光年测试 ==========

    @Test
    void testAuToLightYears() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(63_241.077);
        AstronomicalUnit ly = UnitConverter.auToLightYears(au);
        
        // 63,241.077 AU = 1 光年
        assertEquals(1.0, ly.toLightYears(), 1e-6);
    }

    @Test
    void testAuToLightYears_Zero() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(0.0);
        AstronomicalUnit ly = UnitConverter.auToLightYears(au);
        
        assertEquals(0.0, ly.toLightYears(), 1e-10);
    }

    // ========== 光年转 AU 测试 ==========

    @Test
    void testLightYearsToAU() {
        AstronomicalUnit au = UnitConverter.lightYearsToAU(1.0);
        
        // 1 光年 = 63,241.077 AU
        assertEquals(63_241.077, au.toAU(), 1e-3);
    }

    @Test
    void testLightYearsToAU_Zero() {
        AstronomicalUnit au = UnitConverter.lightYearsToAU(0.0);
        
        assertEquals(0.0, au.toAU(), 1e-10);
    }

    // ========== AU 转秒差距测试 ==========

    @Test
    void testAuToParsecs() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(206_265.0);
        AstronomicalUnit pc = UnitConverter.auToParsecs(au);
        
        // 206,265 AU = 1 秒差距
        assertEquals(1.0, pc.toParsecs(), 1e-6);
    }

    // ========== 秒差距转 AU 测试 ==========

    @Test
    void testParsecsToAU() {
        AstronomicalUnit au = UnitConverter.parsecsToAU(1.0);
        
        // 1 秒差距 = 206,265 AU
        assertEquals(206_265.0, au.toAU(), 1e-3);
    }

    // ========== 转换精度测试 ==========

    @Test
    void testConversionPrecision_AUToLightYears() {
        // 测试 AU 转光年再转回 AU 的精度
        AstronomicalUnit original = AstronomicalUnit.fromAU(100.0);
        AstronomicalUnit ly = UnitConverter.auToLightYears(original);
        AstronomicalUnit converted = UnitConverter.lightYearsToAU(ly.toLightYears());
        
        // 精度应该达到 99.9% 以上
        double precision = Math.abs(100.0 - converted.toAU()) / 100.0;
        assertTrue(precision < 0.001, 
            "AU 转光年再转回 AU 的精度应该达到 99.9% 以上，实际精度: " + (1 - precision) * 100 + "%");
    }

    @Test
    void testConversionPrecision_AUToParsecs() {
        // 测试 AU 转秒差距再转回 AU 的精度
        AstronomicalUnit original = AstronomicalUnit.fromAU(1000.0);
        AstronomicalUnit pc = UnitConverter.auToParsecs(original);
        AstronomicalUnit converted = UnitConverter.parsecsToAU(pc.toParsecs());
        
        // 精度应该达到 99.9% 以上
        double precision = Math.abs(1000.0 - converted.toAU()) / 1000.0;
        assertTrue(precision < 0.001, 
            "AU 转秒差距再转回 AU 的精度应该达到 99.9% 以上，实际精度: " + (1 - precision) * 100 + "%");
    }

    @Test
    void testConversionPrecision_LightYearsToParsecs() {
        // 测试光年转秒差距的精度
        // 1 光年 = 63,241.077 AU，1 秒差距 = 206,265 AU
        // 所以 1 光年 = 63,241.077 / 206,265 ≈ 0.3066 秒差距
        
        AstronomicalUnit ly = AstronomicalUnit.fromLightYears(1.0);
        double pc = ly.toParsecs();
        double expectedPc = 63_241.077 / 206_265.0;
        
        assertEquals(expectedPc, pc, 1e-6);
    }

    // ========== 通用转换方法测试 ==========

    @Test
    void testConvert_AUToLightYears() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(63_241.077);
        AstronomicalUnit result = UnitConverter.convert(au, "AU", "ly");
        
        assertEquals(1.0, result.toLightYears(), 1e-6);
    }

    @Test
    void testConvert_LightYearsToAU() {
        AstronomicalUnit ly = AstronomicalUnit.fromLightYears(1.0);
        AstronomicalUnit result = UnitConverter.convert(ly, "ly", "AU");
        
        assertEquals(63_241.077, result.toAU(), 1e-3);
    }

    @Test
    void testConvert_AUToParsecs() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(206_265.0);
        AstronomicalUnit result = UnitConverter.convert(au, "AU", "pc");
        
        assertEquals(1.0, result.toParsecs(), 1e-6);
    }

    @Test
    void testConvert_ParsecsToAU() {
        AstronomicalUnit pc = AstronomicalUnit.fromParsecs(1.0);
        AstronomicalUnit result = UnitConverter.convert(pc, "pc", "AU");
        
        assertEquals(206_265.0, result.toAU(), 1e-3);
    }

    @Test
    void testConvert_SameUnit() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit result = UnitConverter.convert(au, "AU", "AU");
        
        assertEquals(au.getInternalUnits(), result.getInternalUnits());
    }

    @Test
    void testConvert_InvalidFromUnit() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            UnitConverter.convert(au, "invalid", "AU");
        });
    }

    @Test
    void testConvert_InvalidToUnit() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            UnitConverter.convert(au, "AU", "invalid");
        });
    }

    @Test
    void testConvert_NullValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            UnitConverter.convert(null, "AU", "ly");
        });
    }

    @Test
    void testConvert_NullUnits() {
        AstronomicalUnit au = AstronomicalUnit.fromAU(1.0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            UnitConverter.convert(au, null, "ly");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            UnitConverter.convert(au, "AU", null);
        });
    }

    // ========== 转换常数测试 ==========

    @Test
    void testConversionConstants() {
        // 验证转换常数的值
        assertEquals(149_600_000.0, UnitConverter.AU_TO_KM, 1e-6);
        assertEquals(63_241.077, UnitConverter.LY_TO_AU, 1e-6);
        assertEquals(206_265.0, UnitConverter.PC_TO_AU, 1e-6);
    }
}
