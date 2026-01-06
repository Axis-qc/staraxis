package com.staraxis.game.shared.net.worldgen.snapshot;

import java.util.LinkedHashMap;
import java.util.Map;

public class WorldGenStatsSnapshot {

    private int tileCount;
    private Map<String, Integer> sectorCounts;
    private int galaxyTileCount;
    private int starCount;
    private int planetCount;
    private String starsPerSystemMinMax;

    public WorldGenStatsSnapshot() {
        this.sectorCounts = new LinkedHashMap<>();
    }

    public int getTileCount() {
        return tileCount;
    }

    public void setTileCount(int tileCount) {
        this.tileCount = tileCount;
    }

    public Map<String, Integer> getSectorCounts() {
        return sectorCounts;
    }

    public void setSectorCounts(Map<String, Integer> sectorCounts) {
        this.sectorCounts = sectorCounts != null ? sectorCounts : new LinkedHashMap<>();
    }

    public int getGalaxyTileCount() {
        return galaxyTileCount;
    }

    public void setGalaxyTileCount(int galaxyTileCount) {
        this.galaxyTileCount = galaxyTileCount;
    }

    public int getStarCount() {
        return starCount;
    }

    public void setStarCount(int starCount) {
        this.starCount = starCount;
    }

    public int getPlanetCount() {
        return planetCount;
    }

    public void setPlanetCount(int planetCount) {
        this.planetCount = planetCount;
    }

    public String getStarsPerSystemMinMax() {
        return starsPerSystemMinMax;
    }

    public void setStarsPerSystemMinMax(String starsPerSystemMinMax) {
        this.starsPerSystemMinMax = starsPerSystemMinMax;
    }
}
