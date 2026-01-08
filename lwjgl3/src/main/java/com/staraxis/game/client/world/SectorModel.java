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

    public SectorModel(HexCoord coord, String sectorType, StarSystemSnapshot starSystem) {
        this.coord = coord;
        this.sectorType = sectorType;
        this.starSystem = starSystem;
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
}
