package com.staraxis.game.shared.world;

import java.io.Serializable;

/**
 * 六边形瓦片数据模型 (Hexagonal tile data model).
 */
public class HexTile implements Serializable {

    private final HexCoord coord;
    private String typeId;
    private boolean hasHabitable;

    public HexTile(HexCoord coord, String typeId) {
        this.coord = coord;
        this.typeId = typeId;
        this.hasHabitable = false;
    }

    public HexCoord getCoord() {
        return coord;
    }

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public boolean isHasHabitable() {
        return hasHabitable;
    }

    public void setHasHabitable(boolean hasHabitable) {
        this.hasHabitable = hasHabitable;
    }

    @Override
    public String toString() {
        return "HexTile{"
                + "coord=" + coord
                + ", typeId='" + typeId + '\''
                + ", hasHabitable=" + hasHabitable
                + '}';
    }
}
