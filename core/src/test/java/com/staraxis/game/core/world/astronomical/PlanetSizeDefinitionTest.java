package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.PlanetSizeDefinition;

/**
 * PlanetSizeDefinition 测试类。
 * 
 * 作用（Purpose）：测试 PlanetSizeDefinition 类的所有功能，包括行星大小验证和真实比例验证。
 */
class PlanetSizeDefinitionTest {

    // ========== 构造函数测试 ==========

    @Test
    void testDefaultConstructor() {
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        
        assertNull(def.getPlanetTypeId());
        assertNull(def.getRadiusInAU());
    }

    @Test
    void testConstructor_WithParameters() {
        String typeId = "rocky";
        AstronomicalUnit radius = AstronomicalUnit.fromAU(0.0000426); // 地球半径
        PlanetSizeDefinition def = new PlanetSizeDefinition(typeId, radius);
        
        assertEquals(typeId, def.getPlanetTypeId());
        assertEquals(0.0000426, def.getRadiusInAU().toAU(), 1e-10);
    }

    @Test
    void testConstructor_WithAllParameters() {
        String typeId = "gas_giant";
        AstronomicalUnit radius = AstronomicalUnit.fromAU(0.000477);
        AstronomicalUnit minRadius = AstronomicalUnit.fromAU(0.0001);
        AstronomicalUnit maxRadius = AstronomicalUnit.fromAU(0.001);
        PlanetSizeDefinition def = new PlanetSizeDefinition(typeId, radius, minRadius, maxRadius);
        
        assertEquals(typeId, def.getPlanetTypeId());
        assertEquals(0.000477, def.getRadiusInAU().toAU(), 1e-10);
        assertEquals(0.0001, def.getMinRadius().toAU(), 1e-10);
        assertEquals(0.001, def.getMaxRadius().toAU(), 1e-10);
    }

    // ========== getter/setter 测试 ==========

    @Test
    void testSetRadiusInAU_ValidValue() {
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        AstronomicalUnit radius = AstronomicalUnit.fromAU(0.0000426);
        
        assertDoesNotThrow(() -> {
            def.setRadiusInAU(radius);
        });
        
        assertEquals(0.0000426, def.getRadiusInAU().toAU(), 1e-10);
    }

    @Test
    void testSetRadiusInAU_Null() {
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setRadiusInAU(null);
        });
    }

    @Test
    void testSetRadiusInAU_OutOfRange() {
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        def.setMinRadius(AstronomicalUnit.fromAU(0.0001));
        def.setMaxRadius(AstronomicalUnit.fromAU(0.001));
        
        AstronomicalUnit tooSmall = AstronomicalUnit.fromAU(0.00005);
        assertThrows(IllegalArgumentException.class, () -> {
            def.setRadiusInAU(tooSmall);
        });
    }

    // ========== 验证方法测试 ==========

    @Test
    void testValidate_Success() {
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        def.setPlanetTypeId("rocky");
        def.setRadiusInAU(AstronomicalUnit.fromAU(0.0000426));
        
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testValidate_MissingTypeId() {
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        def.setRadiusInAU(AstronomicalUnit.fromAU(0.0000426));
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.validate();
        });
    }

    // ========== 真实比例验证测试 ==========

    @Test
    void testRealisticProportions_Earth() {
        // 验证地球半径 ≈ 0.0000426 AU
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        def.setPlanetTypeId("rocky");
        AstronomicalUnit earthRadius = AstronomicalUnit.fromAU(0.0000426);
        def.setRadiusInAU(earthRadius);
        
        assertEquals(0.0000426, def.getRadiusInAU().toAU(), 1e-10);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_Jupiter() {
        // 验证木星半径 ≈ 0.000477 AU
        PlanetSizeDefinition def = new PlanetSizeDefinition();
        def.setPlanetTypeId("gas_giant");
        AstronomicalUnit jupiterRadius = AstronomicalUnit.fromAU(0.000477);
        def.setRadiusInAU(jupiterRadius);
        
        assertEquals(0.000477, def.getRadiusInAU().toAU(), 1e-10);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_SizeDifferences() {
        // 验证不同类型行星的大小差异正确
        PlanetSizeDefinition earth = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        PlanetSizeDefinition jupiter = new PlanetSizeDefinition("gas_giant", AstronomicalUnit.fromAU(0.000477));
        
        // 木星应该比地球大
        assertTrue(jupiter.getRadiusInAU().toAU() > earth.getRadiusInAU().toAU());
        
        // 验证比例关系（木星半径约为地球的11倍）
        double ratio = jupiter.getRadiusInAU().toAU() / earth.getRadiusInAU().toAU();
        assertTrue(ratio > 10.0 && ratio < 12.0, "木星半径应该约为地球的11倍，实际比例: " + ratio);
    }

    // ========== equals 和 hashCode 测试 ==========

    @Test
    void testEquals() {
        PlanetSizeDefinition def1 = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        PlanetSizeDefinition def2 = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        PlanetSizeDefinition def3 = new PlanetSizeDefinition("gas_giant", AstronomicalUnit.fromAU(0.000477));
        
        assertEquals(def1, def2);
        assertNotEquals(def1, def3);
        assertNotEquals(def1, null);
        assertNotEquals(def1, "not a PlanetSizeDefinition");
    }

    @Test
    void testHashCode() {
        PlanetSizeDefinition def1 = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        PlanetSizeDefinition def2 = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        
        assertEquals(def1.hashCode(), def2.hashCode());
    }
}
