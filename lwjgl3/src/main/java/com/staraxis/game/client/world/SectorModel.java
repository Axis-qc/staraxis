package com.staraxis.game.client.world;

import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.shared.world.HexCoord;

/**
 * 客户端星区模型（SectorModel）。
 */
public class SectorModel {

    private final HexCoord coord;
    private final String sectorType; // galaxy/nebula/deep_space
    private final StarSystemSnapshot starSystem; // sectorType=galaxy 时可选

    private final double worldPositionXLy;
    private final double worldPositionYLy;

    public SectorModel(HexCoord coord, String sectorType, StarSystemSnapshot starSystem, double worldPositionXLy, double worldPositionYLy) {
        this.coord = coord;
        this.sectorType = sectorType;
        this.starSystem = starSystem;
        this.worldPositionXLy = worldPositionXLy;
        this.worldPositionYLy = worldPositionYLy;
    }

    public HexCoord getCoord() {
        return coord;
    }

    public String getSectorType() {
        return sectorType;
    }

    public StarSystemSnapshot getStarSystem() {
        return starSystem;
    }

    public double getWorldPositionXLy() {
        return worldPositionXLy;
    }

    public double getWorldPositionYLy() {
        return worldPositionYLy;
    }
}
