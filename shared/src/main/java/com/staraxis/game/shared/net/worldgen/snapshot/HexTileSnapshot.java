package com.staraxis.game.shared.net.worldgen.snapshot;

public class HexTileSnapshot {

    private HexCoordSnapshot coord;
    private String typeId;
    private boolean hasHabitable;
    private StarSystemSnapshot starSystem;

    public HexTileSnapshot() {
    }

    public HexCoordSnapshot getCoord() {
        return coord;
    }

    public void setCoord(HexCoordSnapshot coord) {
        this.coord = coord;
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

    public StarSystemSnapshot getStarSystem() {
        return starSystem;
    }

    public void setStarSystem(StarSystemSnapshot starSystem) {
        this.starSystem = starSystem;
    }
}
