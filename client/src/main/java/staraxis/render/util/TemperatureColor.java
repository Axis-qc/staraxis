package staraxis.render.util;

/**
 * TemperatureColor。
 *
 * 将恒星色温（K）映射到 RGB，近似光谱类型颜色。
 */
public final class TemperatureColor {

    private TemperatureColor() {
    }

    private static final float[][] TEMP_COLORS = {
        {30000, 0.59f, 0.71f, 1.0f},   // O: 蓝色
        {10000, 0.67f, 0.78f, 1.0f},   // B: 蓝白
        {7500,  0.85f, 0.89f, 1.0f},   // A: 白色
        {6000,  1.0f,  0.97f, 0.85f},  // F: 黄白
        {5200,  1.0f,  0.92f, 0.60f},  // G: 黄色（太阳）
        {3700,  1.0f,  0.73f, 0.38f},  // K: 橙色
        {0,     1.0f,  0.47f, 0.27f},  // M: 红色
    };

    public static float[] temperatureToRgb(float temperatureK) {
        for (float[] entry : TEMP_COLORS) {
            if (temperatureK >= entry[0]) {
                return new float[]{entry[1], entry[2], entry[3]};
            }
        }
        return new float[]{1f, 0.47f, 0.27f};
    }
}
