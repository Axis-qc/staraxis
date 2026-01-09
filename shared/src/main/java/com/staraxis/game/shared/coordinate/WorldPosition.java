/**
 * 文件作用：定义宏观的、绝对的2D世界坐标。
 * 使用的接口：com.staraxis.game.shared.model.Vector2d
 * 提供的接口：
 *  - WorldPosition(...): 构造函数
 *  - toAbsoluteAU(): 转换为以AU为单位的绝对坐标
 *  - delta(WorldPosition origin): 计算与另一个世界坐标的精确差值
 */
package com.staraxis.game.shared.coordinate;

import com.staraxis.game.shared.model.Vector2d;

/**
 * 世界坐标（World Position）。
 * <p>
 * 用于表示宇宙中的绝对位置，采用分层设计（网格 + 偏移）来避免大尺度下的浮点数精度问题。
 * 本坐标系为平面2D坐标系。
 *
 * @param gridX       世界网格的X轴索引。
 * @param gridY       世界网格的Y轴索引。
 * @param localOffset 在网格内的局部偏移，单位为AU。
 */
public final class WorldPosition {

    /**
     * 每个世界网格单元的边长（单位：AU）。
     * 这个值可以根据游戏世界的大小进行调整，以平衡精度和坐标范围。
     */
    public static final double CELL_SIZE_AU = 1000.0;

    private final long gridX;
    private final long gridY;
    private final Vector2d localOffset;

    public WorldPosition(long gridX, long gridY, Vector2d localOffset) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.localOffset = localOffset;
    }

    public long getGridX() {
        return gridX;
    }

    public long getGridY() {
        return gridY;
    }

    public Vector2d getLocalOffset() {
        return localOffset;
    }

    /**
     * 将分层坐标换算为以AU为单位的绝对坐标。
     * 注意：返回的 Vector2d 可能会因为值过大而丢失精度，主要用于不要求高精度的场景。
     *
     * @return 代表绝对位置的二维向量（单位：AU）。
     */
    public Vector2d toAbsoluteAU() {
        double absoluteX = gridX * CELL_SIZE_AU + localOffset.x;
        double absoluteY = gridY * CELL_SIZE_AU + localOffset.y;
        return new Vector2d(absoluteX, absoluteY);
    }

    /**
     * 计算当前坐标相对于另一个原点坐标的精确差值（单位：AU）。
     * <p>
     * 此方法通过分别计算网格和偏移的差值来避免直接使用绝对坐标相减导致的精度损失，
     * 特别适用于计算相机视野内的局部相对位置。
     *
     * @param origin 作为参考系原点的另一个世界坐标。
     * @return 代表精确差值的二维向量（单位：AU）。
     */
    public Vector2d delta(WorldPosition origin) {
        double gridDeltaX = (double) (this.gridX - origin.gridX) * CELL_SIZE_AU;
        double gridDeltaY = (double) (this.gridY - origin.gridY) * CELL_SIZE_AU;
        
        double offsetDeltaX = this.localOffset.x - origin.localOffset.x;
        double offsetDeltaY = this.localOffset.y - origin.localOffset.y;
        
        return new Vector2d(gridDeltaX + offsetDeltaX, gridDeltaY + offsetDeltaY);
    }

    @Override
    public String toString() {
        return "WorldPosition{" +
                "grid=(" + gridX + "," + gridY + ")" +
                ", localOffset=" + localOffset +
                '}';
    }
}
