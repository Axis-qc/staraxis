package com.staraxis.render.universe;

/**
 * 比例尺格式化工具：将 kmPerPixel 格式化为 `1px = N [unit]`。
 *
 * 约束（来自 Spec FR-8 / Clarifications）：
 * - > 10,000 AU  → ly
 * - > 1,000,000 km → AU
 * - < 1 km → m
 * - 其他 → km
 *
 * 注意：
 * - 本类属于 client 表现层（宪章：UI 层独立），不应放入 core。
 * - 这里不引入“硬枚举”扩散到 shared，仅保留字符串单位符号。
 */
public final class ScaleFormatter {

    // 采用常量，避免魔法数散落；如未来需数据驱动，可替换为配置加载。
    private static final double KM_PER_AU = 149_597_870.7; // 1 AU = 149,597,870.7 km
    private static final double KM_PER_LY = 9.460_730_472_580_8e12; // 1 ly = 9.4607304725808e12 km

    private ScaleFormatter() {
    }

    /**
     * 将 kmPerPixel 格式化为 UI 字符串。
     *
     * @param kmPerPixel 当前每像素代表的公里数
     */
    public static String formatScale(double kmPerPixel) {
        if (Double.isNaN(kmPerPixel) || Double.isInfinite(kmPerPixel) || kmPerPixel <= 0) {
            return "1px = ?";
        }

        // FR-8 阈值：按 km 数值大小切换单位
        if (kmPerPixel > 10_000.0 * KM_PER_AU) {
            double ly = kmPerPixel / KM_PER_LY;
            return String.format("1px = %.2f ly", ly);
        }
        if (kmPerPixel > 1_000_000.0) {
            double au = kmPerPixel / KM_PER_AU;
            return String.format("1px = %.2f AU", au);
        }
        if (kmPerPixel < 1.0) {
            double meters = kmPerPixel * 1000.0;
            return String.format("1px = %.2f m", meters);
        }

        return String.format("1px = %.2f km", kmPerPixel);
    }
}
