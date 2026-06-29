package staraxis.render.util;

/**
 * TemperatureColor。
 *
 * 将恒星色温（K）转换为 RGB 颜色分量。
 * 使用黑体辐射色温近似公式，结果与 SpectralType 预定义颜色一致。
 */
public final class TemperatureColor {

    private TemperatureColor() {
    }

    /**
     * 将色温转换为 RGB 颜色分量。
     *
     * @param temperatureK 色温（开尔文），有效范围 1000-40000K
     * @return 长度为 3 的 float 数组 [r, g, b]，值范围 [0, 1]
     */
    public static float[] temperatureToRgb(float temperatureK) {
        float temp = Math.max(1000, Math.min(40000, temperatureK)) / 100f;

        float r, g, b;

        if (temp <= 66f) {
            r = 1f;
            g = 0.3900815787691529f - 0.6318414437826271f * (float) Math.exp((temp - 60f) / -50f);
            b = temp <= 19f ? 0f
                : 0.5432067893523771f - 0.6352163086295629f * (float) Math.exp((temp - 10f) / -73f);
        } else {
            r = 1.292936186062844f - 0.244391867586561f * (float) Math.exp((temp - 60f) / -100f);
            g = 0.7678445888420641f - 0.1461245536903235f * (float) Math.exp((temp - 60f) / -125f);
            b = 1f;
        }

        return new float[]{
            Math.max(0f, Math.min(1f, r)),
            Math.max(0f, Math.min(1f, g)),
            Math.max(0f, Math.min(1f, b))
        };
    }
}
