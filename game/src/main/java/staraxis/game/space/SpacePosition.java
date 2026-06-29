package staraxis.game.space;

/**
 * SpacePosition（空间位置）。
 *
 * 3D 宇宙坐标系中的绝对位置，单位 GU（Game Unit）。
 * 右手坐标系：X 向右，Y 向上（星系盘面法线），Z 向前。
 * 原点为星系中心 (0, 0, 0)。
 *
 * 整个游戏空间 = 100,000 x 100,000 x 100,000 GU 正方体，坐标范围 +-50,000 GU。
 */
public record SpacePosition(double x, double y, double z) {

    /** 深空（不属于任何恒星系）的 parentSystemId。 */
    public static final long DEEP_SPACE = 0L;

    /** 原点 (0, 0, 0)。 */
    public static final SpacePosition ORIGIN = new SpacePosition(0, 0, 0);

    /**
     * 计算与另一个位置的距离（GU）。
     */
    public double distanceTo(SpacePosition other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 计算与另一个位置的距离平方（避免开方，用于比较）。
     */
    public double distanceSquaredTo(SpacePosition other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 与另一个位置相加（向量加法）。
     */
    public SpacePosition add(SpacePosition other) {
        return new SpacePosition(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    /**
     * 与向量相加。
     */
    public SpacePosition add(double dx, double dy, double dz) {
        return new SpacePosition(this.x + dx, this.y + dy, this.z + dz);
    }

    /**
     * 标量乘法。
     */
    public SpacePosition scale(double factor) {
        return new SpacePosition(this.x * factor, this.y * factor, this.z * factor);
    }

    /**
     * 到原点的距离。
     */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    @Override
    public String toString() {
        return String.format("SpacePosition(%.2f, %.2f, %.2f)", x, y, z);
    }
}
