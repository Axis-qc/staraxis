package com.staraxis.game.shared.world;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 生成后的世界地图容器 (World map container). 包含配置快照、边界半径和所有瓦片。
 */
public class WorldMap implements Serializable {

    private final WorldGenConfig config;
    private final int boundsRadius;
    private final Map<HexCoord, HexTile> tiles;

    public WorldMap(WorldGenConfig config, int boundsRadius) {
        this.config = config;
        this.boundsRadius = boundsRadius;
        this.tiles = new HashMap<>();
    }

    public WorldGenConfig getConfig() {
        return config;
    }

    public int getBoundsRadius() {
        return boundsRadius;
    }

    public Map<HexCoord, HexTile> getTiles() {
        return Collections.unmodifiableMap(tiles);
    }

    public void addTile(HexTile tile) {
        tiles.put(tile.getCoord(), tile);
    }

    public HexTile getTile(HexCoord coord) {
        return tiles.get(coord);
    }

    @Override
    public String toString() {
        return "WorldMap{"
                + "tilesCount=" + tiles.size()
                + ", boundsRadius=" + boundsRadius
                + ", config=" + config
                + '}';
    }
}
