package com.staraxis.game.shared.net.worldgen.snapshot;

import java.util.ArrayList;
import java.util.List;

public class WorldSnapshot {

    private long seedValue;
    private int boundsRadius;
    private WorldGenStatsSnapshot stats;
    private List<HexTileSnapshot> tiles;

    public WorldSnapshot() {
        this.tiles = new ArrayList<>();
    }

    public long getSeedValue() {
        return seedValue;
    }

    public void setSeedValue(long seedValue) {
        this.seedValue = seedValue;
    }

    public int getBoundsRadius() {
        return boundsRadius;
    }

    public void setBoundsRadius(int boundsRadius) {
        this.boundsRadius = boundsRadius;
    }

    public WorldGenStatsSnapshot getStats() {
        return stats;
    }

    public void setStats(WorldGenStatsSnapshot stats) {
        this.stats = stats;
    }

    public List<HexTileSnapshot> getTiles() {
        return tiles;
    }

    public void setTiles(List<HexTileSnapshot> tiles) {
        this.tiles = tiles != null ? tiles : new ArrayList<>();
    }
}
