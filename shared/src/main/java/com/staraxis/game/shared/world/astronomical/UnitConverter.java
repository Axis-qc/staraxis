package com.staraxis.game.shared.world.astronomical;

/**
 * 单位转换器（Unit Converter）。
 * 
 * 作用（Purpose）：处理 AU、光年、秒差距之间的转换。
 * 实现方式：工具类，所有方法为静态方法，使用预定义的转换常数。
 * 
 * 依赖（Dependencies）：AstronomicalUnit。
 * 对外接口（Public API）：单位转换方法（auToLightYears, lightYearsToAU, 
 * auToParsecs, parsecsToAU, convert）。
 */
public class UnitConverter {

    /**
     * 禁止实例化（工具类）。
     */
    private UnitConverter() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * AU 到公里的转换常数：1 AU = 149,600,000 公里。
     */
    public static final double AU_TO_KM = 149_600_000.0;

    /**
     * 光年到 AU 的转换常数：1 光年 = 63,241.077 AU（精确值）。
     */
    public static final double LY_TO_AU = 63_241.077;

    /**
     * 秒差距到 AU 的转换常数：1 秒差距 = 206,265 AU（精确值）。
     */
    public static final double PC_TO_AU = 206_265.0;

    /**
     * AU 转光年。
     * 
     * @param au AstronomicalUnit 实例（以 AU 为单位）
     * @return 转换后的 AstronomicalUnit 实例（内部存储为对应的光年值，以 AU 为单位）
     */
    public static AstronomicalUnit auToLightYears(AstronomicalUnit au) {
        if (au == null) {
            throw new IllegalArgumentException("天文单位不能为空");
        }
        
        // 转换为光年：ly = au / LY_TO_AU
        // 返回一个新的 AstronomicalUnit，其值等于输入值除以 LY_TO_AU
        double lyValue = au.toAU() / LY_TO_AU;
        return AstronomicalUnit.fromLightYears(lyValue);
    }

    /**
     * 光年转 AU。
     * 
     * @param lyValue 光年值（double）
     * @return 转换后的 AstronomicalUnit 实例（以 AU 为单位）
     */
    public static AstronomicalUnit lightYearsToAU(double lyValue) {
        if (!Double.isFinite(lyValue)) {
            throw new IllegalArgumentException("光年值必须为有限数值");
        }
        
        // 转换为 AU：au = ly * LY_TO_AU
        double auValue = lyValue * LY_TO_AU;
        return AstronomicalUnit.fromAU(auValue);
    }

    /**
     * AU 转秒差距。
     * 
     * @param au AstronomicalUnit 实例（以 AU 为单位）
     * @return 转换后的 AstronomicalUnit 实例（内部存储为对应的秒差距值，以 AU 为单位）
     */
    public static AstronomicalUnit auToParsecs(AstronomicalUnit au) {
        if (au == null) {
            throw new IllegalArgumentException("天文单位不能为空");
        }
        
        // 转换为秒差距：pc = au / PC_TO_AU
        // 返回一个新的 AstronomicalUnit，其值等于输入值除以 PC_TO_AU
        double pcValue = au.toAU() / PC_TO_AU;
        return AstronomicalUnit.fromParsecs(pcValue);
    }

    /**
     * 秒差距转 AU。
     * 
     * @param pcValue 秒差距值（double）
     * @return 转换后的 AstronomicalUnit 实例（以 AU 为单位）
     */
    public static AstronomicalUnit parsecsToAU(double pcValue) {
        if (!Double.isFinite(pcValue)) {
            throw new IllegalArgumentException("秒差距值必须为有限数值");
        }
        
        // 转换为 AU：au = pc * PC_TO_AU
        double auValue = pcValue * PC_TO_AU;
        return AstronomicalUnit.fromAU(auValue);
    }

    /**
     * 通用转换方法。
     * 
     * @param value AstronomicalUnit 实例
     * @param fromUnit 源单位（"AU", "ly", "pc"）
     * @param toUnit 目标单位（"AU", "ly", "pc"）
     * @return 转换后的 AstronomicalUnit 实例
     * @throws IllegalArgumentException 如果单位不支持或参数无效
     */
    public static AstronomicalUnit convert(
            AstronomicalUnit value,
            String fromUnit,
            String toUnit) {
        
        if (value == null) {
            throw new IllegalArgumentException("天文单位值不能为空");
        }
        if (fromUnit == null || toUnit == null) {
            throw new IllegalArgumentException("单位标识不能为空");
        }
        
        // 标准化单位标识（忽略大小写）
        String from = fromUnit.trim().toLowerCase();
        String to = toUnit.trim().toLowerCase();
        
        // 如果源单位和目标单位相同，直接返回
        if (from.equals(to)) {
            return value;
        }
        
        // 先转换为 AU（中间单位）
        // 注意：AstronomicalUnit 内部总是以 AU 为单位存储
        // 如果源单位是 AU，直接使用；如果是其他单位，需要先转换为 AU 值
        AstronomicalUnit auValue;
        if (from.equals("au")) {
            auValue = value;
        } else if (from.equals("ly") || from.equals("lightyears") || from.equals("light_years")) {
            // 假设输入的 value 已经是光年值（以 AU 存储但表示光年）
            // 需要先获取光年值，再转换为 AU
            double ly = value.toLightYears();
            auValue = lightYearsToAU(ly);
        } else if (from.equals("pc") || from.equals("parsecs")) {
            // 假设输入的 value 已经是秒差距值（以 AU 存储但表示秒差距）
            // 需要先获取秒差距值，再转换为 AU
            double pc = value.toParsecs();
            auValue = parsecsToAU(pc);
        } else {
            throw new IllegalArgumentException("不支持的源单位: " + fromUnit);
        }
        
        // 从 AU 转换为目标单位
        if (to.equals("au")) {
            return auValue;
        } else if (to.equals("ly") || to.equals("lightyears") || to.equals("light_years")) {
            return auToLightYears(auValue);
        } else if (to.equals("pc") || to.equals("parsecs")) {
            return auToParsecs(auValue);
        } else {
            throw new IllegalArgumentException("不支持的目标单位: " + toUnit);
        }
    }
}
