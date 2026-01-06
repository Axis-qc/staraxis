package com.staraxis.game.core.world.astronomical;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import com.staraxis.game.shared.world.astronomical.SectorSizeDefinition;
import com.staraxis.game.shared.world.astronomical.UnitConverter;
import com.staraxis.game.shared.world.astronomical.VisualScaleConfig;

/**
 * 天文单位系统（Astronomical Unit System）。
 * 
 * 作用（Purpose）：天文单位系统的核心类，负责配置加载和初始化。
 * 实现方式：从配置文件加载系统配置，验证配置的合理性，提供系统访问接口。
 * 
 * 依赖（Dependencies）：UnitConverter, 配置文件 astronomical-units-config.properties。
 * 对外接口（Public API）：loadFromConfig(), validate(), getUnitConverter()。
 */
public class AstronomicalUnitSystem {

    private static final Logger LOGGER = Logger.getLogger(AstronomicalUnitSystem.class.getName());
    private static final String CONFIG_FILE = "i18n/astronomical-units-config.properties";
    private static final String SECTOR_SIZE_CONFIG_FILE = "i18n/sector-size-config.properties";
    private static final String VISUAL_SCALE_CONFIG_FILE = "i18n/visual-scale-config.properties";

    /**
     * 缩放因子（从配置文件加载）。
     */
    private long scaleFactor;

    /**
     * 系统版本（从配置文件加载）。
     */
    private String version;

    /**
     * 星区大小定义（从配置文件加载）。
     */
    private SectorSizeDefinition sectorSizeDefinition;

    /**
     * 可视化缩放配置（从配置文件加载）。
     */
    private VisualScaleConfig visualScaleConfig;

    // 单位转换器是工具类，不需要实例字段

    /**
     * 私有构造函数，使用工厂方法创建实例。
     */
    private AstronomicalUnitSystem() {
    }

    /**
     * 从配置文件加载天文单位系统。
     * 
     * @return AstronomicalUnitSystem 实例
     * @throws IOException 如果配置文件读取失败
     * @throws IllegalArgumentException 如果配置文件格式错误
     */
    public static AstronomicalUnitSystem loadFromConfig() throws IOException {
        AstronomicalUnitSystem system = new AstronomicalUnitSystem();
        system.loadConfiguration();
        system.validate();
        return system;
    }

    /**
     * 从配置文件加载配置。
     * 
     * @throws IOException 如果配置文件读取失败
     * @throws IllegalArgumentException 如果配置文件格式错误
     */
    private void loadConfiguration() throws IOException {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new IOException("配置文件未找到: " + CONFIG_FILE);
            }
            props.load(is);
        } catch (IOException e) {
            LOGGER.severe("加载配置文件失败: " + CONFIG_FILE + " - " + e.getMessage());
            throw e;
        }

        // 加载缩放因子
        String scaleFactorStr = props.getProperty("astronomical.units.scale.factor");
        if (scaleFactorStr == null || scaleFactorStr.trim().isEmpty()) {
            throw new IllegalArgumentException("配置文件中缺少 astronomical.units.scale.factor");
        }
        try {
            this.scaleFactor = Long.parseLong(scaleFactorStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("缩放因子格式错误: " + scaleFactorStr, e);
        }

        // 加载系统版本
        this.version = props.getProperty("astronomical.units.version", "1.0.0");

        // 验证转换常数（从配置文件读取，但应该与 UnitConverter 中的常量一致）
        String auToKmStr = props.getProperty("astronomical.units.au.to.km");
        String lyToAuStr = props.getProperty("astronomical.units.ly.to.au");
        String pcToAuStr = props.getProperty("astronomical.units.pc.to.au");

        if (auToKmStr != null) {
            double auToKm = Double.parseDouble(auToKmStr);
            if (Math.abs(auToKm - UnitConverter.AU_TO_KM) > 1e-6) {
                LOGGER.warning("配置文件中的 AU_TO_KM 值与代码中的常量不一致: " + 
                               auToKm + " vs " + UnitConverter.AU_TO_KM);
            }
        }

        if (lyToAuStr != null) {
            double lyToAu = Double.parseDouble(lyToAuStr);
            if (Math.abs(lyToAu - UnitConverter.LY_TO_AU) > 1e-6) {
                LOGGER.warning("配置文件中的 LY_TO_AU 值与代码中的常量不一致: " + 
                               lyToAu + " vs " + UnitConverter.LY_TO_AU);
            }
        }

        if (pcToAuStr != null) {
            double pcToAu = Double.parseDouble(pcToAuStr);
            if (Math.abs(pcToAu - UnitConverter.PC_TO_AU) > 1e-6) {
                LOGGER.warning("配置文件中的 PC_TO_AU 值与代码中的常量不一致: " + 
                               pcToAu + " vs " + UnitConverter.PC_TO_AU);
            }
        }

        LOGGER.info("天文单位系统加载成功，版本: " + this.version + ", 缩放因子: " + this.scaleFactor);
        
        // 加载星区大小定义
        loadSectorSizeDefinition();
        
        // 加载可视化缩放配置
        loadVisualScaleConfig();
    }

    /**
     * 从配置文件加载星区大小定义。
     * 
     * @throws IOException 如果配置文件读取失败
     * @throws IllegalArgumentException 如果配置文件格式错误
     */
    private void loadSectorSizeDefinition() throws IOException {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SECTOR_SIZE_CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("星区大小配置文件未找到: " + SECTOR_SIZE_CONFIG_FILE + "，使用默认值");
                this.sectorSizeDefinition = new SectorSizeDefinition();
                return;
            }
            props.load(is);
        } catch (IOException e) {
            LOGGER.warning("加载星区大小配置文件失败: " + SECTOR_SIZE_CONFIG_FILE + " - " + e.getMessage() + "，使用默认值");
            this.sectorSizeDefinition = new SectorSizeDefinition();
            return;
        }

        // 加载默认星区大小（光年）
        String defaultLightYearsStr = props.getProperty("sector.size.default.lightYears");
        double defaultLightYears = 1.0; // 默认 1 光年
        if (defaultLightYearsStr != null && !defaultLightYearsStr.trim().isEmpty()) {
            try {
                defaultLightYears = Double.parseDouble(defaultLightYearsStr.trim());
            } catch (NumberFormatException e) {
                LOGGER.warning("星区大小默认值格式错误: " + defaultLightYearsStr + "，使用默认值 1.0 光年");
            }
        }

        // 加载是否可配置
        String configurableStr = props.getProperty("sector.size.configurable", "true");
        boolean isConfigurable = Boolean.parseBoolean(configurableStr);

        // 创建星区大小定义
        this.sectorSizeDefinition = new SectorSizeDefinition(
            com.staraxis.game.shared.world.astronomical.AstronomicalUnit.fromLightYears(defaultLightYears),
            isConfigurable
        );

        LOGGER.info("星区大小定义加载成功: " + defaultLightYears + " 光年 (" + 
                   this.sectorSizeDefinition.getSizeInAU().toAU() + " AU), 可配置: " + isConfigurable);
    }

    /**
     * 验证系统配置的合理性。
     * 
     * @throws IllegalArgumentException 如果配置不合理
     */
    public void validate() throws IllegalArgumentException {
        // 验证缩放因子
        if (scaleFactor <= 0) {
            throw new IllegalArgumentException("缩放因子必须 > 0，当前值: " + scaleFactor);
        }

        // 验证缩放因子是否为 10^12（标准值）
        long expectedScaleFactor = 1_000_000_000_000L; // 10^12
        if (scaleFactor != expectedScaleFactor) {
            LOGGER.warning("缩放因子不是标准值 10^12，当前值: " + scaleFactor);
        }

        // 验证版本
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("系统版本不能为空");
        }

        // 验证星区大小定义
        if (sectorSizeDefinition != null) {
            sectorSizeDefinition.validate();
        }

        // 验证可视化缩放配置
        if (visualScaleConfig != null) {
            // VisualScaleConfig 的验证在设置时已完成
        }
    }

    /**
     * 获取缩放因子。
     * 
     * @return 缩放因子
     */
    public long getScaleFactor() {
        return scaleFactor;
    }

    /**
     * 获取系统版本。
     * 
     * @return 系统版本
     */
    public String getVersion() {
        return version;
    }

    /**
     * 获取单位转换器（工具类引用）。
     * 
     * @return UnitConverter 类（工具类，不需要实例）
     */
    public Class<UnitConverter> getUnitConverter() {
        return UnitConverter.class;
    }

    /**
     * 获取星区大小定义。
     * 
     * @return 星区大小定义
     */
    public SectorSizeDefinition getSectorSizeDefinition() {
        return sectorSizeDefinition;
    }

    /**
     * 从配置文件加载可视化缩放配置。
     * 
     * @throws IOException 如果配置文件读取失败
     * @throws IllegalArgumentException 如果配置文件格式错误
     */
    private void loadVisualScaleConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(VISUAL_SCALE_CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("可视化缩放配置文件未找到: " + VISUAL_SCALE_CONFIG_FILE + "，使用默认值");
                this.visualScaleConfig = new VisualScaleConfig();
                return;
            }
            props.load(is);
        } catch (IOException e) {
            LOGGER.warning("加载可视化缩放配置文件失败: " + VISUAL_SCALE_CONFIG_FILE + " - " + e.getMessage() + "，使用默认值");
            this.visualScaleConfig = new VisualScaleConfig();
            return;
        }

        // 加载 AU 到像素的转换比例
        String auToPixelsStr = props.getProperty("visual.scale.au.to.pixels", "0.00079");
        float auToPixels = Float.parseFloat(auToPixelsStr);

        // 加载自动缩放设置
        String autoEnabledStr = props.getProperty("visual.scale.auto.enabled", "true");
        boolean autoScaleEnabled = Boolean.parseBoolean(autoEnabledStr);

        // 加载基础缩放比例
        String starBaseStr = props.getProperty("visual.scale.star.base", "1.0");
        float starBaseScale = Float.parseFloat(starBaseStr);
        
        String planetBaseStr = props.getProperty("visual.scale.planet.base", "0.5");
        float planetBaseScale = Float.parseFloat(planetBaseStr);
        
        String orbitBaseStr = props.getProperty("visual.scale.orbit.base", "1.0");
        float orbitBaseScale = Float.parseFloat(orbitBaseStr);

        // 加载手动缩放因子
        String manualFactorStr = props.getProperty("visual.scale.manual.factor", "1.0");
        float manualScaleFactor = Float.parseFloat(manualFactorStr);

        // 加载自动缩放范围
        String autoMinStr = props.getProperty("visual.scale.auto.min.factor", "0.1");
        float autoMinFactor = Float.parseFloat(autoMinStr);
        
        String autoMaxStr = props.getProperty("visual.scale.auto.max.factor", "10.0");
        float autoMaxFactor = Float.parseFloat(autoMaxStr);

        // 创建可视化缩放配置
        this.visualScaleConfig = new VisualScaleConfig(
            auToPixels, autoScaleEnabled, manualScaleFactor,
            starBaseScale, planetBaseScale, orbitBaseScale,
            autoMinFactor, autoMaxFactor
        );

        LOGGER.info("可视化缩放配置加载成功: auToPixels=" + auToPixels + 
                   ", autoScaleEnabled=" + autoScaleEnabled);
    }

    /**
     * 获取可视化缩放配置。
     * 
     * @return 可视化缩放配置
     */
    public VisualScaleConfig getVisualScaleConfig() {
        return visualScaleConfig;
    }

    @Override
    public String toString() {
        return "AstronomicalUnitSystem{version='" + version + 
               "', scaleFactor=" + scaleFactor + "}";
    }
}
