package staraxis.render.lod;

/**
 * LodCalculator。
 *
 * 根据相机到天体的距离计算 LOD 层级，最远为 POINT（光点），不隐藏。
 */
public final class LodCalculator {

    public static final double FULL_MAX = 50_000.0;
    public static final double LOW_MAX = 200_000.0;

    // ── 行星 UI 圆标 LOD（淡入淡出） ──
    /** 圆标完全透明距离（镜头近于此距离时不画圆标）。 */
    public static final double DOT_FADE_NEAR = 5_000.0;
    /** 圆标完全不透明距离（镜头远于此距离时 alpha 饱和）。 */
    public static final double DOT_FADE_FAR = 20_000.0;
    /** 圆标最大 alpha。 */
    public static final float DOT_MAX_ALPHA = 0.9f;

    // ── 轨道环 LOD（淡入淡出） ──
    /** 轨道环完全透明距离。 */
    public static final double ORBIT_FADE_NEAR = 5_000.0;
    /** 轨道环完全不透明距离。 */
    public static final double ORBIT_FADE_FAR = 20_000.0;
    /** 轨道环最大 alpha。 */
    public static final float ORBIT_MAX_ALPHA = 0.9f;

    private LodCalculator() {
    }

    public static LodLevel calculate(double distanceToCamera) {
        if (distanceToCamera < FULL_MAX) {
            return LodLevel.FULL;
        } else if (distanceToCamera < LOW_MAX) {
            return LodLevel.LOW;
        } else {
            return LodLevel.POINT;
        }
    }

    /**
     * 计算轨道环 alpha，基于相机 orbitDist 做线性淡入淡出。
     *
     * @param orbitDist 相机到系统中心的轨道距离
     * @return alpha [0, ORBIT_MAX_ALPHA]，0 = 完全透明（不渲染）
     */
    public static float calculateOrbitAlpha(double orbitDist) {
        if (orbitDist <= ORBIT_FADE_NEAR) return 0f;
        if (orbitDist >= ORBIT_FADE_FAR) return ORBIT_MAX_ALPHA;
        return ORBIT_MAX_ALPHA * (float) ((orbitDist - ORBIT_FADE_NEAR) / (ORBIT_FADE_FAR - ORBIT_FADE_NEAR));
    }

    /**
     * 计算行星 UI 圆标 alpha，基于相机 orbitDist 做线性淡入淡出。
     *
     * @param orbitDist 相机到系统中心的轨道距离
     * @return alpha [0, DOT_MAX_ALPHA]，0 = 完全透明（不渲染）
     */
    public static float calculateDotAlpha(double orbitDist) {
        if (orbitDist <= DOT_FADE_NEAR) return 0f;
        if (orbitDist >= DOT_FADE_FAR) return DOT_MAX_ALPHA;
        return DOT_MAX_ALPHA * (float) ((orbitDist - DOT_FADE_NEAR) / (DOT_FADE_FAR - DOT_FADE_NEAR));
    }
}
