package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.staraxis.game.shared.world.astronomical.UnitConverter;

/**
 * AstronomicalUnitSystem 测试类。
 * 
 * 作用（Purpose）：测试 AstronomicalUnitSystem 类的配置加载、验证和确定性计算功能。
 */
class AstronomicalUnitSystemTest {

    // ========== 配置加载测试 ==========

    @Test
    void testLoadFromConfig_Success() throws IOException {
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        assertNotNull(system);
        assertNotNull(system.getVersion());
        assertTrue(system.getScaleFactor() > 0);
    }

    @Test
    void testLoadFromConfig_ScaleFactor() throws IOException {
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        // 验证缩放因子为标准值 10^12
        long expectedScaleFactor = 1_000_000_000_000L;
        assertEquals(expectedScaleFactor, system.getScaleFactor());
    }

    @Test
    void testLoadFromConfig_Version() throws IOException {
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        // 验证版本不为空
        assertNotNull(system.getVersion());
        assertFalse(system.getVersion().trim().isEmpty());
    }

    @Test
    void testLoadFromConfig_UnitConverter() throws IOException {
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        // 验证单位转换器类引用
        assertNotNull(system.getUnitConverter());
        assertEquals(UnitConverter.class, system.getUnitConverter());
    }

    // ========== 系统验证测试 ==========

    @Test
    void testValidate_Success() throws IOException {
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        // 验证方法应该不抛出异常
        assertDoesNotThrow(() -> {
            system.validate();
        });
    }

    // ========== 确定性计算验证测试 ==========

    @Test
    void testDeterminism_SameConfigSameResult() throws IOException {
        // 相同配置应该产生相同结果
        AstronomicalUnitSystem system1 = AstronomicalUnitSystem.loadFromConfig();
        AstronomicalUnitSystem system2 = AstronomicalUnitSystem.loadFromConfig();
        
        assertEquals(system1.getScaleFactor(), system2.getScaleFactor());
        assertEquals(system1.getVersion(), system2.getVersion());
    }

    @Test
    void testDeterminism_ScaleFactorConsistency() throws IOException {
        // 验证缩放因子与 AstronomicalUnit 中的常量一致
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        // AstronomicalUnit 中的 SCALE_FACTOR 应该是 10^12
        long expectedScaleFactor = 1_000_000_000_000L;
        assertEquals(expectedScaleFactor, system.getScaleFactor());
    }

    @Test
    void testDeterminism_ConversionConstantsConsistency() throws IOException {
        // 验证转换常数与 UnitConverter 中的常量一致
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        // 系统应该能够访问 UnitConverter 的常量
        assertNotNull(system.getUnitConverter());
        
        // 验证常量值
        assertEquals(149_600_000.0, UnitConverter.AU_TO_KM, 1e-6);
        assertEquals(63_241.077, UnitConverter.LY_TO_AU, 1e-6);
        assertEquals(206_265.0, UnitConverter.PC_TO_AU, 1e-6);
    }

    // ========== toString 测试 ==========

    @Test
    void testToString() throws IOException {
        AstronomicalUnitSystem system = AstronomicalUnitSystem.loadFromConfig();
        
        String str = system.toString();
        assertNotNull(str);
        assertTrue(str.contains("version"));
        assertTrue(str.contains("scaleFactor"));
    }
}
