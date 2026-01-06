package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.astronomical.OrbitSizeDefinition;
import com.staraxis.game.shared.world.astronomical.PlanetSizeDefinition;
import com.staraxis.game.shared.world.astronomical.SectorSizeDefinition;
import com.staraxis.game.shared.world.astronomical.StarSizeDefinition;

/**
 * SizeDefinition 综合测试类。
 * 
 * 作用（Purpose）：测试所有大小定义类的综合验证，确保它们之间的比例关系正确。
 */
class SizeDefinitionTest {

    // ========== 所有大小定义的综合验证测试 ==========

    @Test
    void testAllSizeDefinitions_RealisticProportions() {
        // 验证所有大小定义符合真实宇宙比例
        
        // 星区大小：1 光年
        SectorSizeDefinition sector = new SectorSizeDefinition();
        double sectorSizeInAU = sector.getSizeInAU().toAU();
        
        // 轨道大小：地球轨道 1 AU
        OrbitSizeDefinition orbit = new OrbitSizeDefinition(AstronomicalUnit.fromAU(1.0));
        double orbitSizeInAU = orbit.getSemiMajorAxis().toAU();
        
        // 恒星大小：太阳半径 0.00465 AU
        StarSizeDefinition star = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        double starRadiusInAU = star.getRadiusInAU().toAU();
        
        // 行星大小：地球半径 0.0000426 AU
        PlanetSizeDefinition planet = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        double planetRadiusInAU = planet.getRadiusInAU().toAU();
        
        // 验证比例关系
        // 1. 星区应该远大于轨道
        assertTrue(sectorSizeInAU > orbitSizeInAU * 1000, 
            "星区大小应该远大于轨道大小");
        
        // 2. 轨道应该远大于恒星半径
        assertTrue(orbitSizeInAU > starRadiusInAU * 100, 
            "轨道大小应该远大于恒星半径");
        
        // 3. 恒星半径应该远大于行星半径
        assertTrue(starRadiusInAU > planetRadiusInAU * 100, 
            "恒星半径应该远大于行星半径");
    }

    @Test
    void testAllSizeDefinitions_Validation() {
        // 验证所有大小定义都能通过验证
        
        SectorSizeDefinition sector = new SectorSizeDefinition();
        assertDoesNotThrow(() -> {
            sector.validate();
        });
        
        OrbitSizeDefinition orbit = new OrbitSizeDefinition(AstronomicalUnit.fromAU(1.0));
        assertDoesNotThrow(() -> {
            orbit.validate();
        });
        
        StarSizeDefinition star = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        assertDoesNotThrow(() -> {
            star.validate();
        });
        
        PlanetSizeDefinition planet = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        assertDoesNotThrow(() -> {
            planet.validate();
        });
    }

    @Test
    void testAllSizeDefinitions_Consistency() {
        // 验证所有大小定义使用相同的单位系统（AstronomicalUnit）
        
        SectorSizeDefinition sector = new SectorSizeDefinition();
        OrbitSizeDefinition orbit = new OrbitSizeDefinition(AstronomicalUnit.fromAU(1.0));
        StarSizeDefinition star = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        PlanetSizeDefinition planet = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        
        // 所有大小定义都应该使用 AstronomicalUnit
        assertNotNull(sector.getSizeInAU());
        assertNotNull(orbit.getSemiMajorAxis());
        assertNotNull(star.getRadiusInAU());
        assertNotNull(planet.getRadiusInAU());
        
        // 验证它们都可以转换为 AU
        assertTrue(sector.getSizeInAU().toAU() > 0);
        assertTrue(orbit.getSemiMajorAxis().toAU() > 0);
        assertTrue(star.getRadiusInAU().toAU() > 0);
        assertTrue(planet.getRadiusInAU().toAU() > 0);
    }

    @Test
    void testAllSizeDefinitions_RealisticSolarSystem() {
        // 验证真实太阳系的比例关系
        
        // 太阳半径
        StarSizeDefinition sun = new StarSizeDefinition("yellow_dwarf", AstronomicalUnit.fromAU(0.00465));
        double sunRadius = sun.getRadiusInAU().toAU();
        
        // 地球轨道
        OrbitSizeDefinition earthOrbit = new OrbitSizeDefinition(AstronomicalUnit.fromAU(1.0));
        double earthOrbitSize = earthOrbit.getSemiMajorAxis().toAU();
        
        // 地球半径
        PlanetSizeDefinition earth = new PlanetSizeDefinition("rocky", AstronomicalUnit.fromAU(0.0000426));
        double earthRadius = earth.getRadiusInAU().toAU();
        
        // 验证：地球轨道半径应该远大于太阳半径
        assertTrue(earthOrbitSize > sunRadius * 200, 
            "地球轨道半径应该远大于太阳半径（约215倍）");
        
        // 验证：太阳半径应该远大于地球半径
        assertTrue(sunRadius > earthRadius * 100, 
            "太阳半径应该远大于地球半径（约109倍）");
    }
}
