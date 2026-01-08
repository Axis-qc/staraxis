package com.staraxis.game.core.coordinate;

/**
 * 世界坐标（分层坐标）：WorldCoord 大尺度整型网格 + LocalOffset 局部 double 偏移。
 *
 * 设计目标：
 * - 支持星系级巨大坐标范围（避免 float 精度崩溃）
 * - 近景渲染/拾取可使用局部化后的 double 偏移保持平滑
 *
 * 单位约定：
 * - 游戏逻辑层以 km 作为基础距离单位
 * - 每个网格单元大小固定为 1,000,000 km（Spec FR-2 / Clarifications）
 */
public final class WorldCoordinate {

    /** 1 个 WorldCoord 网格单元的大小（km）。 */
    public static final long CELL_SIZE_KM = 1_000_000L;

    private final int gridX;
    private final int gridY;
    private final int gridZ;

    /**
     * 局部偏移（km）。
     * 约束建议：[-CELL_SIZE_KM/2, CELL_SIZE_KM/2] 以内（实现中不强制裁剪，交由上层保证）。
     */
    private final double offsetXKm;
    private final double offsetYKm;
    private final double offsetZKm;

    public WorldCoordinate(int gridX, int gridY, int gridZ, double offsetXKm, double offsetYKm, double offsetZKm) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridZ = gridZ;
        this.offsetXKm = offsetXKm;
        this.offsetYKm = offsetYKm;
        this.offsetZKm = offsetZKm;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public int getGridZ() {
        return gridZ;
    }

    public double getOffsetXKm() {
        return offsetXKm;
    }

    public double getOffsetYKm() {
        return offsetYKm;
    }

    public double getOffsetZKm() {
        return offsetZKm;
    }

    /**
     * 将分层坐标换算成“绝对公里”坐标（可能非常大，仅用于显示/调试，不建议直接喂给渲染管线）。
     */
    public double toAbsoluteXKm() {
        return gridX * (double) CELL_SIZE_KM + offsetXKm;
    }

    public double toAbsoluteYKm() {
        return gridY * (double) CELL_SIZE_KM + offsetYKm;
    }

    public double toAbsoluteZKm() {
        return gridZ * (double) CELL_SIZE_KM + offsetZKm;
    }

    /**
     * 返回相对另一坐标的“局部化”差值（km）。
     * 用于渲染前局部化：renderPosKm = worldPosKm - cameraPosKm。
     */
    public double deltaXKm(WorldCoordinate origin) {
        // 避免直接用 toAbsoluteKm() 相减导致 double 精度在巨大数值下丢失
        // 而是分别计算 grid 和 offset 的差值
        double gridDelta = (double) (this.gridX - origin.gridX) * CELL_SIZE_KM;
        double offsetDelta = this.offsetXKm - origin.offsetXKm;
        return gridDelta + offsetDelta;
    }

    public double deltaYKm(WorldCoordinate origin) {
        double gridDelta = (double) (this.gridY - origin.gridY) * CELL_SIZE_KM;
        double offsetDelta = this.offsetYKm - origin.offsetYKm;
        return gridDelta + offsetDelta;
    }

    public double deltaZKm(WorldCoordinate origin) {
        double gridDelta = (double) (this.gridZ - origin.gridZ) * CELL_SIZE_KM;
        double offsetDelta = this.offsetZKm - origin.offsetZKm;
        return gridDelta + offsetDelta;
    }

    @Override
    public String toString() {
        return "WorldCoordinate{" +
                "grid=(" + gridX + "," + gridY + "," + gridZ + ")" +
                ", offsetKm=(" + offsetXKm + "," + offsetYKm + "," + offsetZKm + ")" +
                '}';
    }
}
