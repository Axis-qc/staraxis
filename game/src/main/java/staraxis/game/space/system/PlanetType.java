package staraxis.game.space.system;

import staraxis.game.space.OrbitalElements;

/**
 * PlanetType（行星类型）。
 *
 * 决定行星的视觉外观（颜色、大小范围）。
 */
public enum PlanetType {

    /** 岩石行星（灰褐色）。 */
    ROCKY(0.55f, 0.47f, 0.38f, 25.0, 45.0),

    /** 气态巨星（橙黄/条纹）。 */
    GAS_GIANT(0.85f, 0.65f, 0.35f, 500.0, 800.0),

    /** 海洋行星（蓝色）。 */
    OCEAN(0.20f, 0.45f, 0.80f, 50.0, 70.0),

    /** 冰封行星（白色/浅蓝）。 */
    ICE(0.75f, 0.82f, 0.90f, 20.0, 35.0),

    /** 熔岩行星（暗红色）。 */
    LAVA(0.70f, 0.25f, 0.15f, 40.0, 60.0);

    /** RGB 颜色分量（0-1）。 */
    public final float colorR;
    public final float colorG;
    public final float colorB;

    /** 半径范围（GU）。 */
    public final double minRadiusGU;
    public final double maxRadiusGU;

    PlanetType(float r, float g, float b, double minR, double maxR) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.minRadiusGU = minR;
        this.maxRadiusGU = maxR;
    }
}
