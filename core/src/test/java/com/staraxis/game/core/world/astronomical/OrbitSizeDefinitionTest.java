package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.OrbitSizeDefinition;

/**
 * OrbitSizeDefinition 测试类。
 * 
 * 作用（Purpose）：测试 OrbitSizeDefinition 类的所有功能，包括轨道大小验证和真实比例验证。
 */
class OrbitSizeDefinitionTest {

    // ========== 构造函数测试 ==========

    @Test
    void testDefaultConstructor() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        
        assertNotNull(def.getSemiMajorAxis());
        // 默认值应该是 1 AU（地球轨道）
        assertEquals(1.0, def.getSemiMajorAxis().toAU(), 1e-10);
        assertNotNull(def.getMinValue());
        assertNotNull(def.getMaxValue());
    }

    @Test
    void testConstructor_WithSemiMajorAxis() {
        AstronomicalUnit axis = AstronomicalUnit.fromAU(5.2); // 木星轨道
        OrbitSizeDefinition def = new OrbitSizeDefinition(axis);
        
        assertEquals(5.2, def.getSemiMajorAxis().toAU(), 1e-10);
    }

    @Test
    void testConstructor_WithAllParameters() {
        AstronomicalUnit axis = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit min = AstronomicalUnit.fromAU(0.1);
        AstronomicalUnit max = AstronomicalUnit.fromAU(100.0);
        OrbitSizeDefinition def = new OrbitSizeDefinition(axis, min, max);
        
        assertEquals(1.0, def.getSemiMajorAxis().toAU(), 1e-10);
        assertEquals(0.1, def.getMinValue().toAU(), 1e-10);
        assertEquals(100.0, def.getMaxValue().toAU(), 1e-10);
    }

    // ========== getter/setter 测试 ==========

    @Test
    void testSetSemiMajorAxis_ValidValue() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit newAxis = AstronomicalUnit.fromAU(5.2);
        
        assertDoesNotThrow(() -> {
            def.setSemiMajorAxis(newAxis);
        });
        
        assertEquals(5.2, def.getSemiMajorAxis().toAU(), 1e-10);
    }

    @Test
    void testSetSemiMajorAxis_Null() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setSemiMajorAxis(null);
        });
    }

    @Test
    void testSetSemiMajorAxis_Zero() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit zeroAxis = AstronomicalUnit.fromAU(0.0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setSemiMajorAxis(zeroAxis);
        });
    }

    @Test
    void testSetSemiMajorAxis_OutOfRange() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit tooLarge = AstronomicalUnit.fromAU(200.0); // 超出默认最大值 100 AU
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setSemiMajorAxis(tooLarge);
        });
    }

    @Test
    void testSetMinValue() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit newMin = AstronomicalUnit.fromAU(0.5);
        
        assertDoesNotThrow(() -> {
            def.setMinValue(newMin);
        });
        
        assertEquals(0.5, def.getMinValue().toAU(), 1e-10);
    }

    @Test
    void testSetMaxValue() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit newMax = AstronomicalUnit.fromAU(200.0);
        
        assertDoesNotThrow(() -> {
            def.setMaxValue(newMax);
        });
        
        assertEquals(200.0, def.getMaxValue().toAU(), 1e-10);
    }

    @Test
    void testSetMaxValue_LessThanMin() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit min = AstronomicalUnit.fromAU(10.0);
        AstronomicalUnit max = AstronomicalUnit.fromAU(5.0); // 小于最小值
        
        def.setMinValue(min);
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setMaxValue(max);
        });
    }

    // ========== 验证方法测试 ==========

    @Test
    void testValidate_Success() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        
        // 默认值应该通过验证
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testValidate_OutOfRange() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        // setSemiMajorAxis 在设置时就会检查范围，所以这里直接测试 setSemiMajorAxis 会抛出异常
        AstronomicalUnit tooLarge = AstronomicalUnit.fromAU(200.0);
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setSemiMajorAxis(tooLarge);
        });
        
        // 测试通过反射或直接设置字段后验证的情况（如果需要）
        // 由于 setSemiMajorAxis 已经验证，这里测试一个在范围内的值
        AstronomicalUnit validValue = AstronomicalUnit.fromAU(50.0);
        def.setSemiMajorAxis(validValue);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    // ========== 真实比例验证测试 ==========

    @Test
    void testRealisticProportions_Earth() {
        // 验证地球轨道 = 1 AU
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit earthOrbit = AstronomicalUnit.fromAU(1.0);
        def.setSemiMajorAxis(earthOrbit);
        
        assertEquals(1.0, def.getSemiMajorAxis().toAU(), 1e-10);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_Jupiter() {
        // 验证木星轨道 ≈ 5.2 AU
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit jupiterOrbit = AstronomicalUnit.fromAU(5.203);
        def.setSemiMajorAxis(jupiterOrbit);
        
        assertEquals(5.203, def.getSemiMajorAxis().toAU(), 1e-10);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_Mercury() {
        // 验证水星轨道 ≈ 0.387 AU
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit mercuryOrbit = AstronomicalUnit.fromAU(0.387);
        def.setSemiMajorAxis(mercuryOrbit);
        
        assertEquals(0.387, def.getSemiMajorAxis().toAU(), 1e-10);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_Neptune() {
        // 验证海王星轨道 ≈ 30.069 AU
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        AstronomicalUnit neptuneOrbit = AstronomicalUnit.fromAU(30.069);
        def.setSemiMajorAxis(neptuneOrbit);
        
        assertEquals(30.069, def.getSemiMajorAxis().toAU(), 1e-10);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_OrbitStability() {
        // 验证轨道大小在合理范围内（0.1 - 100 AU，默认范围）
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        
        // 测试内边界（使用默认最小值 0.1 AU）
        AstronomicalUnit innerBound = AstronomicalUnit.fromAU(0.1);
        def.setSemiMajorAxis(innerBound);
        assertDoesNotThrow(() -> {
            def.validate();
        });
        
        // 测试外边界（使用默认最大值 100 AU）
        AstronomicalUnit outerBound = AstronomicalUnit.fromAU(100.0);
        def.setSemiMajorAxis(outerBound);
        assertDoesNotThrow(() -> {
            def.validate();
        });
        
        // 测试扩展范围的情况
        OrbitSizeDefinition def2 = new OrbitSizeDefinition();
        def2.setMinValue(AstronomicalUnit.fromAU(0.01));
        def2.setMaxValue(AstronomicalUnit.fromAU(1000.0));
        AstronomicalUnit extendedInner = AstronomicalUnit.fromAU(0.01);
        def2.setSemiMajorAxis(extendedInner);
        assertDoesNotThrow(() -> {
            def2.validate();
        });
    }

    // ========== equals 和 hashCode 测试 ==========

    @Test
    void testEquals() {
        OrbitSizeDefinition def1 = new OrbitSizeDefinition();
        OrbitSizeDefinition def2 = new OrbitSizeDefinition();
        OrbitSizeDefinition def3 = new OrbitSizeDefinition(AstronomicalUnit.fromAU(5.2));
        
        assertEquals(def1, def2);
        assertNotEquals(def1, def3);
        assertNotEquals(def1, null);
        assertNotEquals(def1, "not an OrbitSizeDefinition");
    }

    @Test
    void testHashCode() {
        OrbitSizeDefinition def1 = new OrbitSizeDefinition();
        OrbitSizeDefinition def2 = new OrbitSizeDefinition();
        
        assertEquals(def1.hashCode(), def2.hashCode());
    }

    // ========== toString 测试 ==========

    @Test
    void testToString() {
        OrbitSizeDefinition def = new OrbitSizeDefinition();
        String str = def.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("OrbitSizeDefinition"));
        assertTrue(str.contains("AU"));
    }
}
