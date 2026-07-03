package staraxis.game.space;

/**
 * SpacePosition（空间位置）。
 *
 * 3D 宇宙坐标系中的绝对位置，单位 GU（Game Unit）。
 * 右手坐标系：X 向右，Y 向上（星系盘面法线），Z 向前。
 * 原点为星系中心 (0, 0, 0)。
 *
 * 区块坐标（chunkX/Y/Z）将空间划分为 CHUNK_SIZE 的正方体区块，
 * 区块局部坐标（localX/Y/Z）将坐标映射到 [-CHUNK_SIZE/2, CHUNK_SIZE/2) 范围，
 * 确保转换成 float 时精度步长 ~0.0005 GU，用于 GPU 渲染。
 *
 * 整个游戏空间 = 100,000 x 100,000 x 100,000 GU 正方体，坐标范围 +-50,000 GU。
 */
public record SpacePosition(double x, double y, double z) {

    /** 深空（不属于任何恒星系）的 parentSystemId。 */
    public static final long DEEP_SPACE = 0L;

    /** 区块大小（GU），用于双重坐标系渲染。 */
    public static final double CHUNK_SIZE = 10_000.0;

    /** 区块大小的一半，用于局部坐标计算。 */
    private static final double HALF_CHUNK = CHUNK_SIZE / 2.0;

    /** 原点 (0, 0, 0)。 */
    public static final SpacePosition ORIGIN = new SpacePosition(0, 0, 0);

    // ── 区块坐标（双重坐标系） ────────────────────────────────

    /** X 轴区块坐标。 */
    public int chunkX() { return (int) Math.floor(x / CHUNK_SIZE); }

    /** Y 轴区块坐标。 */
    public int chunkY() { return (int) Math.floor(y / CHUNK_SIZE); }

    /** Z 轴区块坐标。 */
    public int chunkZ() { return (int) Math.floor(z / CHUNK_SIZE); }

    /** 区块局部 X 坐标（范围 [-HALF_CHUNK, HALF_CHUNK)），float 精度 0.0005 GU。 */
    public double localX() { return x - chunkX() * CHUNK_SIZE - HALF_CHUNK; }

    /** 区块局部 Y 坐标（范围 [-HALF_CHUNK, HALF_CHUNK)），float 精度 0.0005 GU。 */
    public double localY() { return y - chunkY() * CHUNK_SIZE - HALF_CHUNK; }

    /** 区块局部 Z 坐标（范围 [-HALF_CHUNK, HALF_CHUNK)），float 精度 0.0005 GU。 */
    public double localZ() { return z - chunkZ() * CHUNK_SIZE - HALF_CHUNK; }

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
