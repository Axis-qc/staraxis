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
}
