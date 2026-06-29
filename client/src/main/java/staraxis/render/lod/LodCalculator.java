package staraxis.render.lod;

/**
 * LodCalculator。
 *
 * 根据相机到天体的距离计算 LOD 层级，最远为 POINT（光点），不隐藏。
 */
public final class LodCalculator {

    public static final double FULL_MAX = 50_000.0;
    public static final double LOW_MAX = 200_000.0;

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
}
