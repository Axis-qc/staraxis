package staraxis.game.world;

import staraxis.game.world.hex.SectorCoord;

/**
 * WorldHexLayout
 *
 * pointy-top axial hex 布局的星区中心点换算（单位：GU）。
 */
public final class WorldHexLayout {

    private WorldHexLayout() {
    }

    public static Vec2d sectorCenterWorld2D_GU(SectorCoord s) {
        double size = WorldConstants.SECTOR_SIZE_GU;
        double x = size * (Math.sqrt(3.0) * s.q() + Math.sqrt(3.0) / 2.0 * s.r());
        double y = size * (3.0 / 2.0 * s.r());
        return new Vec2d(x, y);
    }

    /**
     * 将世界坐标转换为最近的星区坐标喵。
     *
     * @param worldPos 世界坐标（GU）
     * @return 对应的星区坐标
     */
    public static SectorCoord worldToSectorCoord(Vec2d worldPos) {
        double size = WorldConstants.SECTOR_SIZE_GU;
        double x = worldPos.x();
        double y = worldPos.y();

        // 逆变换公式：从世界坐标解算轴向坐标喵
        // 正向公式：
        // x = size * (√3 * q + √3/2 * r)
        // y = size * (3/2 * r)
        // 解得：
        // r = (2 * y) / (3 * size)
        // q = (x / (size * √3)) - r / 2

        double r = (2.0 * y) / (3.0 * size);
        double q = (x / (size * Math.sqrt(3.0))) - r / 2.0;

        // 四舍五入到最近的整数六边形坐标喵
        // 使用立方坐标进行四舍五入，以获得更准确的结果喵
        double xCube = q;
        double zCube = r;
        double yCube = -xCube - zCube;

        int rx = (int) Math.round(xCube);
        int ry = (int) Math.round(yCube);
        int rz = (int) Math.round(zCube);

        // 检查四舍五入后立方坐标之和是否为0（立方坐标约束）喵
        if (rx + ry + rz != 0) {
            // 调整：找出误差最大的坐标并重新计算喵
            double dx = Math.abs(rx - xCube);
            double dy = Math.abs(ry - yCube);
            double dz = Math.abs(rz - zCube);

            if (dx > dy && dx > dz) {
                rx = -ry - rz;
            } else if (dy > dz) {
                ry = -rx - rz;
            } else {
                rz = -rx - ry;
            }
        }

        // 转换回轴向坐标：q = rx, r = rz
        return new SectorCoord(rx, rz);
    }
}
