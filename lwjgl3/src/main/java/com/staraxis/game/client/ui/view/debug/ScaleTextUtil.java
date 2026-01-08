package com.staraxis.game.client.ui.view.debug;

/**
 * client 表现层的比例尺文本格式化。
 *
 * 直接实现 Spec FR-8 的阈值规则：
 * - > 10,000 AU  → ly
 * - > 1,000,000 km → AU
 * - < 1 km → m
 * - 其他 → km
 */
public final class ScaleTextUtil {

    private static final double KM_PER_AU = 149_597_870.7;
    private static final double KM_PER_LY = 9.460_730_472_580_8e12;

    private ScaleTextUtil() {
    }

    public static String formatScale(double kmPerPixel) {
        if (Double.isNaN(kmPerPixel) || Double.isInfinite(kmPerPixel) || kmPerPixel <= 0) {
            return "1px = ?";
        }

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
