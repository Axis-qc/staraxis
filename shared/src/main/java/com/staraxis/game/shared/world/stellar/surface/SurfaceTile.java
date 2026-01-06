package com.staraxis.game.shared.world.stellar.surface;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.staraxis.game.shared.model.Vector2;

public class SurfaceTile implements Serializable {

    private String tileId;
    private boolean pentagon;
    private List<String> neighborTileIds;
    private Vector2 direction;

    public SurfaceTile() {
        this.neighborTileIds = new ArrayList<>();
    }

    public SurfaceTile(String tileId, boolean pentagon, List<String> neighborTileIds) {
        this.tileId = tileId;
        this.pentagon = pentagon;
        setNeighborTileIds(neighborTileIds);
    }

    public SurfaceTile(String tileId, boolean pentagon, List<String> neighborTileIds, Vector2 direction) {
        this.tileId = tileId;
        this.pentagon = pentagon;
        setNeighborTileIds(neighborTileIds);
        setDirection(direction);
    }

    public String getTileId() {
        return tileId;
    }

    public void setTileId(String tileId) {
        this.tileId = tileId;
    }

    public boolean isPentagon() {
        return pentagon;
    }

    public void setPentagon(boolean pentagon) {
        this.pentagon = pentagon;
    }

    public List<String> getNeighborTileIds() {
        return Collections.unmodifiableList(neighborTileIds);
    }

    public void setNeighborTileIds(List<String> neighborTileIds) {
        if (neighborTileIds == null) {
            this.neighborTileIds = new ArrayList<>();
            return;
        }
        this.neighborTileIds = new ArrayList<>(neighborTileIds);
    }

    public Vector2 getDirection() {
        return direction;
    }

    public void setDirection(Vector2 direction) {
        this.direction = direction;
    }
}
