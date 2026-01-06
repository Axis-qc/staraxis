package com.staraxis.game.shared.net.worldgen.snapshot;

public class PlanetSnapshot {

    private String id;
    private String planetTypeId;
    private int orbitIndex;

    public PlanetSnapshot() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlanetTypeId() {
        return planetTypeId;
    }

    public void setPlanetTypeId(String planetTypeId) {
        this.planetTypeId = planetTypeId;
    }

    public int getOrbitIndex() {
        return orbitIndex;
    }

    public void setOrbitIndex(int orbitIndex) {
        this.orbitIndex = orbitIndex;
    }
}
