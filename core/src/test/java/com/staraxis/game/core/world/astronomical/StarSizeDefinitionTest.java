package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.StarSizeDefinition;

/**
 * StarSizeDefinition 测试类。
 * 
 * 作用（Purpose）：测试 StarSizeDefinition 类的所有功能，包括恒星大小验证和真实比例验证。
 */
class StarSizeDefinitionTest {

    // ========== 构造函数测试 ==========

    @Test
    void testDefaultConstructor() {
        StarSizeDefinition def = new StarSizeDefinition();
        
        assertNull(def.getStarTypeId());
        assertNull(def.getRadiusInAU());
    }

    @Test
    void testConstructor_WithParameters() {
        String typeId = "yellow_dwarf";
        AstronomicalUnit radius = AstronomicalUnit.fromAU(0.00465); // 太阳半径
        StarSizeDefinition def = new StarSizeDefinition(typeId, radius);
        
        assertEquals(typeId, def.getStarTypeId());
        assertEquals(0.00465, def.getRadiusInAU().toAU(), 1e-8);
    }

    @Test
    void testConstructor_WithAllParameters() {
        String typeId = "red_giant";
        AstronomicalUnit radius = AstronomicalUnit.fromAU(5.0);
        AstronomicalUnit minRadius = AstronomicalUnit.fromAU(1.0);
        AstronomicalUnit maxRadius = AstronomicalUnit.fromAU(10.0);
        StarSizeDefinition def = new StarSizeDefinition(typeId, radius, minRadius, maxRadius);
        
        assertEquals(typeId, def.getStarTypeId());
        assertEquals(5.0, def.getRadiusInAU().toAU(), 1e-10);
        assertEquals(1.0, def.getMinRadius().toAU(), 1e-10);
        assertEquals(10.0, def.getMaxRadius().toAU(), 1e-10);
    }

    // ========== getter/setter 测试 ==========

    @Test
    void testSetRadiusInAU_ValidValue() {
        StarSizeDefinition def = new StarSizeDefinition();
        AstronomicalUnit radius = AstronomicalUnit.fromAU(0.00465);
        
        assertDoesNotThrow(() -> {
            def.setRadiusInAU(radius);
        });
        
        assertEquals(0.00465, def.getRadiusInAU().toAU(), 1e-8);
    }

    @Test
    void testSetRadiusInAU_Null() {
        StarSizeDefinition def = new StarSizeDefinition();
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.setRadiusInAU(null);
        });
    }

    @Test
    void testSetRadiusInAU_OutOfRange() {
        StarSizeDefinition def = new StarSizeDefinition();
        def.setMinRadius(AstronomicalUnit.fromAU(1.0));
        def.setMaxRadius(AstronomicalUnit.fromAU(10.0));
        
        AstronomicalUnit tooSmall = AstronomicalUnit.fromAU(0.5);
        assertThrows(IllegalArgumentException.class, () -> {
            def.setRadiusInAU(tooSmall);
        });
    }

    // ========== 验证方法测试 ==========

    @Test
    void testValidate_Success() {
        StarSizeDefinition def = new StarSizeDefinition();
        def.setStarTypeId("yellow_dwarf");
        def.setRadiusInAU(AstronomicalUnit.fromAU(0.00465));
        
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testValidate_MissingTypeId() {
        StarSizeDefinition def = new StarSizeDefinition();
        def.setRadiusInAU(AstronomicalUnit.fromAU(0.00465));
        
        assertThrows(IllegalArgumentException.class, () -> {
            def.validate();
        });
    }

    // ========== 真实比例验证测试 ==========

    @Test
    void testRealisticProportions_Sun() {
        // 验证太阳半径 ≈ 0.00465 AU
        StarSizeDefinition def = new StarSizeDefinition();
        def.setStarTypeId("yellow_dwarf");
        AstronomicalUnit sunRadius = AstronomicalUnit.fromAU(0.00465);
        def.setRadiusInAU(sunRadius);
        
        assertEquals(0.00465, def.getRadiusInAU().toAU(), 1e-8);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_RedGiant() {
        // 验证红巨星半径范围 1-10 AU
        StarSizeDefinition def = new StarSizeDefinition();
        def.setStarTypeId("red_giant");
        def.setMinRadius(AstronomicalUnit.fromAU(1.0));
        def.setMaxRadius(AstronomicalUnit.fromAU(10.0));
        AstronomicalUnit redGiantRadius = AstronomicalUnit.fromAU(5.0);
        def.setRadiusInAU(redGiantRadius);
        
        assertEquals(5.0, def.getRadiusInAU().toAU(), 1e-10);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_WhiteDwarf() {
        // 验证白矮星半径 ≈ 0.0001 AU
        StarSizeDefinition def = new StarSizeDefinition();
        def.setStarTypeId("white_dwarf");
        AstronomicalUnit whiteDwarfRadius = AstronomicalUnit.fromAU(0.0001);
        def.setRadiusInAU(whiteDwarfRadius);
        
        assertEquals(0.0001, def.getRadiusInAU().toAU(), 1e-8);
        assertDoesNotThrow(() -> {
            def.validate();
        });
    }

    @Test
    void testRealisticProportions_SizeDifferences() {
        // 验证不同类型恒星的大小差异正确
        StarSizeDefinition sun = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        StarSizeDefinition redGiant = new StarSizeDefinition("red_giant", AstronomicalUnit.fromAU(5.0));
        StarSizeDefinition whiteDwarf = new StarSizeDefinition("white_dwarf", AstronomicalUnit.fromAU(0.0001));
        
        // 红巨星应该比太阳大
        assertTrue(redGiant.getRadiusInAU().toAU() > sun.getRadiusInAU().toAU());
        
        // 白矮星应该比太阳小
        assertTrue(whiteDwarf.getRadiusInAU().toAU() < sun.getRadiusInAU().toAU());
    }

    // ========== equals 和 hashCode 测试 ==========

    @Test
    void testEquals() {
        StarSizeDefinition def1 = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        StarSizeDefinition def2 = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        StarSizeDefinition def3 = new StarSizeDefinition("red_giant", AstronomicalUnit.fromAU(5.0));
        
        assertEquals(def1, def2);
        assertNotEquals(def1, def3);
        assertNotEquals(def1, null);
        assertNotEquals(def1, "not a StarSizeDefinition");
    }

    @Test
    void testHashCode() {
        StarSizeDefinition def1 = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        StarSizeDefinition def2 = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        
        assertEquals(def1.hashCode(), def2.hashCode());
    }
}
