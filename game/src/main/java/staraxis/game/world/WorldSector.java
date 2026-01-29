package staraxis.game.world;

import staraxis.game.world.hex.SectorCoord;

public class WorldSector {

    public final SectorCoord coord;

    /**
     * 星区中心点（权威世界坐标，GU，2D）。
     */
    public final Vec2d centerWorldGU;

    /**
     * 预留：该星区归属/势力占位（可为空）。
     */
    public String ownerNationId;

    public WorldSector(SectorCoord coord, Vec2d centerWorldGU) {
        this.coord = coord;
        this.centerWorldGU = centerWorldGU;
    }
}
