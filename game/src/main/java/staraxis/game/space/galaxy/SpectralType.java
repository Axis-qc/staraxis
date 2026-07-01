package staraxis.game.space.galaxy;

/**
 * SpectralType（恒星光谱类型）。
 *
 * 哈佛光谱分类：O/B/A/F/G/K/M，从蓝到红。
 * 决定恒星的颜色、温度、大小范围。
 */
public enum SpectralType {

    /** O 型：蓝色，温度 > 30000K，半径大。 */
    O(0.59f, 0.71f, 1.0f, 150000.0, 300000.0),

    /** B 型：蓝白色，10000-30000K。 */
    B(0.67f, 0.78f, 1.0f, 20000.0, 40000.0),

    /** A 型：白色，7500-10000K。 */
    A(0.85f, 0.89f, 1.0f, 10000.0, 14000.0),

    /** F 型：黄白色，6000-7500K。 */
    F(1.0f, 0.97f, 0.85f, 7000.0, 10000.0),

    /** G 型：黄色（太阳），5200-6000K。 */
    G(1.0f, 0.92f, 0.60f, 6000.0, 8000.0),

    /** K 型：橙色，3700-5200K。 */
    K(1.0f, 0.73f, 0.38f, 4000.0, 6000.0),

    /** M 型：红色（最常见），2400-3700K。 */
    M(1.0f, 0.47f, 0.27f, 1500.0, 2500.0);

    /** RGB 颜色分量（0-1）。 */
    public final float colorR;
    public final float colorG;
    public final float colorB;

    /** 半径范围（GU）。 */
    public final double minRadiusGU;
    public final double maxRadiusGU;

    SpectralType(float r, float g, float b, double minR, double maxR) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.minRadiusGU = minR;
        this.maxRadiusGU = maxR;
    }
}
