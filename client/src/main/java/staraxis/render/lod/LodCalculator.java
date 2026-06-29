package staraxis.render.lod;

/**
 * LodCalculator（LOD 计算器）。
 *
 * 根据相机到天体的距离计算 LOD 层级。
 *
 * 距离阈值（GU）：
 * - 0 ~ 10,000：FULL（全精度）
 * - 10,000 ~ 30,000：LOW（低精度）
 * - 30,000 ~ 50,000：POINT（光点）
 * - > 50,000：HIDDEN（不渲染）
 */
public final class LodCalculator {

    /** FULL 层级最大距离（GU）。 */
    public static final double FULL_MAX = 10_000.0;

    /** LOW 层级最大距离（GU）。 */
    public static final double LOW_MAX = 30_000.0;

    /** POINT 层级最大距离（GU）。 */
    public static final double POINT_MAX = 50_000.0;

    private LodCalculator() {
    }

    /**
     * 根据距离计算 LOD 层级。
     *
     * @param distanceToCamera 相机到天体的距离（GU）
     * @return LOD 层级
     */
    public static LodLevel calculate(double distanceToCamera) {
        if (distanceToCamera < FULL_MAX) {
            return LodLevel.FULL;
        } else if (distanceToCamera < LOW_MAX) {
            return LodLevel.LOW;
        } else if (distanceToCamera < POINT_MAX) {
            return LodLevel.POINT;
        } else {
            return LodLevel.HIDDEN;
        }
    }
}
