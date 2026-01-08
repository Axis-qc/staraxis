package com.staraxis.game.shared.net.worldgen.snapshot;

/**
 * 星区快照（SectorSnapshot）：对应一个六边形星区。
 */
public class SectorSnapshot {

    private HexCoordSnapshot coord;

    /**
     * 星区类型：
     * - galaxy: 星系星区（包含一个 star_system）
     * - nebula: 星云
     * - deep_space: 深空
     */
    private String sectorType;

    /**
     * 当 sectorType=galaxy 时存在。
     */
    private StarSystemSnapshot starSystem;

    public SectorSnapshot() {
    }

    public HexCoordSnapshot getCoord() {
        return coord;
    }

    public void setCoord(HexCoordSnapshot coord) {
        this.coord = coord;
    }

    public String getSectorType() {
        return sectorType;
    }

    public void setSectorType(String sectorType) {
        this.sectorType = sectorType;
    }

    public StarSystemSnapshot getStarSystem() {
        return starSystem;
    }

    public void setStarSystem(StarSystemSnapshot starSystem) {
        this.starSystem = starSystem;
    }
}