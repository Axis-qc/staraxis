package com.staraxis.game.shared.world;

/**
 * 六边形数学工具类 (Hexagonal math utilities). 提供邻居查找、距离计算等立方体坐标操作。
 */
public class HexMath {

    /**
     * 六个方向的偏移量（立方体坐标）
     */
    public static final HexCoord[] DIRECTIONS = {
        HexCoord.of(1, -1, 0), HexCoord.of(1, 0, -1), HexCoord.of(0, 1, -1),
        HexCoord.of(-1, 1, 0), HexCoord.of(-1, 0, 1), HexCoord.of(0, -1, 1)
    };

    /**
     * 获取指定方向的邻居坐标偏移
     */
    public static HexCoord direction(int direction) {
        return DIRECTIONS[direction % 6];
    }

    /**
     * 获取邻居坐标
     */
    public static HexCoord neighbor(HexCoord coord, int direction) {
        return coord.add(direction(direction));
    }

    /**
     * 计算两个坐标之间的距离
     */
    public static int distance(HexCoord a, HexCoord b) {
        return (Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ())) / 2;
    }
}
