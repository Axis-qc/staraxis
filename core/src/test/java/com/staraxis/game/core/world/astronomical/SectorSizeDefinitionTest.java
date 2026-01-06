package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.SectorSizeDefinition;

/**
 * SectorSizeDefinition 测试类。
 * 
 * 作用（Purpose）：测试 SectorSizeDefinition 类的所有功能，包括星区大小验证和配置。
 */
class SectorSizeDefinitionTest {

    // ========== 构造函数测试 ==========

    @Test
    void testDefaultConstructor() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        
        assertNotNull(def.getSizeInAU());
        // 默认值应该是 1 光年
        assertEquals(1.0, def.getSizeInLightYears(), 1e-6);
        assertTrue(def.isConfigurable());
    }

    @Test
    void testConstructor_WithParameters() {
        AstronomicalUnit size = AstronomicalUnit.fromLightYears(2.0);
        SectorSizeDefinition def = new SectorSizeDefinition(size, false);
        
        assertEquals(2.0, def.getSizeInLightYears(), 1e-6);
        assertFalse(def.isConfigurable());
    }

    // ========== getter/setter 测试 ==========

    @Test
    void testGetSizeInAU() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        AstronomicalUnit size = def.getSizeInAU();
        
        assertNotNull(size);
        assertEquals(1.0, size.toLightYears(), 1e-6);
    }

    @Test
    void testGetSizeInLightYears() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        double sizeInLy = def.getSizeInLightYears();
        
        assertEquals(1.0, sizeInLy, 1e-6);
    }

    @Test
    void testSetSizeInAU_ValidValue() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        AstronomicalUnit newSize = AstronomicalUnit.fromLightYears(2.0);
        
        assertDoesNotThrow(() -> {
            def.setSizeInAU(newSize);
        });
        
        assertEquals(2.0, def.getSizeInLightYears(), 1e-6);
    }

    @Test
    void testSetSizeInAU_Null() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setSizeInAU(null);
        });
    }

    @Test
    void testSetSizeInAU_Zero() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        AstronomicalUnit zeroSize = AstronomicalUnit.fromAU(0.0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setSizeInAU(zeroSize);
        });
    }

    @Test
    void testSetSizeInAU_Negative() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        AstronomicalUnit negativeSize = AstronomicalUnit.fromAU(-1.0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setSizeInAU(negativeSize);
        });
    }

    @Test
    void testSetConfigurable() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        
        def.setConfigurable(false);
        assertFalse(def.isConfigurable());
        
        def.setConfigurable(true);
        assertTrue(def.isConfigurable());
    }

    // ========== 验证方法测试 ==========

    @Test
    void testValidate_Success() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        
        // 默认值应该通过验证
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testValidate_TooSmall() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        // 设置一个很小的值（小于 0.1 光年）
        AstronomicalUnit smallSize = AstronomicalUnit.fromLightYears(0.05);
        def.setSizeInAU(smallSize);
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.validate();
        });
    }

    @Test
    void testValidate_ValidMinimum() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        // 设置最小值（1.0 光年，满足验证要求 >= 1 光年）
        AstronomicalUnit minSize = AstronomicalUnit.fromLightYears(1.0);
        def.setSizeInAU(minSize);
        
        // 应该通过验证
        assertDoesNotThrow(() -> {
            def.validate();
        });
        
        // 测试略大于最小值的情况（1.1 光年）
        AstronomicalUnit slightlyAboveMin = AstronomicalUnit.fromLightYears(1.1);
        def.setSizeInAU(slightlyAboveMin);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testValidate_LargeValue() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        // 设置一个较大的值（10 光年）
        AstronomicalUnit largeSize = AstronomicalUnit.fromLightYears(10.0);
        def.setSizeInAU(largeSize);
        
        // 应该通过验证
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    // ========== 星区大小验证测试 ==========

    @Test
    void testSectorSize_CanAccommodateStarSystem() {
        // 验证星区大小能够合理容纳一个恒星系（建议 >= 1 光年）
        SectorSizeDefinition def = new SectorSizeDefinition();
        
        // 默认 1 光年应该能够容纳恒星系
        double sizeInLy = def.getSizeInLightYears();
        assertTrue(sizeInLy >= 1.0, "星区大小应该 >= 1 光年以容纳恒星系，当前值: " + sizeInLy);
    }

    @Test
    void testSectorSize_RealisticProportions() {
        // 验证星区大小符合真实宇宙比例
        SectorSizeDefinition def = new SectorSizeDefinition();
        
        // 1 光年 = 63,241.077 AU
        double sizeInAU = def.getSizeInAU().toAU();
        double expectedAU = 63_241.077;
        
        assertEquals(expectedAU, sizeInAU, 1e-3, 
            "1 光年应该等于 63,241.077 AU，当前值: " + sizeInAU);
    }

    // ========== equals 和 hashCode 测试 ==========

    @Test
    void testEquals() {
        SectorSizeDefinition def1 = new SectorSizeDefinition();
        SectorSizeDefinition def2 = new SectorSizeDefinition();
        SectorSizeDefinition def3 = new SectorSizeDefinition(
            AstronomicalUnit.fromLightYears(2.0), true);
        
        assertEquals(def1, def2);
        assertNotEquals(def1, def3);
        assertNotEquals(def1, null);
        assertNotEquals(def1, "not a SectorSizeDefinition");
    }

    @Test
    void testHashCode() {
        SectorSizeDefinition def1 = new SectorSizeDefinition();
        SectorSizeDefinition def2 = new SectorSizeDefinition();
        
        assertEquals(def1.hashCode(), def2.hashCode());
    }

    // ========== toString 测试 ==========

    @Test
    void testToString() {
        SectorSizeDefinition def = new SectorSizeDefinition();
        String str = def.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("SectorSizeDefinition"));
        assertTrue(str.contains("AU") || str.contains("ly"));
    }
}
