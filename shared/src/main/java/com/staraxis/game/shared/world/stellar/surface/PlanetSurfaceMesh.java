package com.staraxis.game.shared.world.stellar.surface;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.staraxis.game.shared.model.Vector2;

public class PlanetSurfaceMesh implements Serializable {

    private MeshResolutionLevel resolutionLevel;
    private List<SurfaceTile> tiles;

    public PlanetSurfaceMesh() {
        this.tiles = new ArrayList<>();
    }

    public PlanetSurfaceMesh(MeshResolutionLevel resolutionLevel, List<SurfaceTile> tiles) {
        setResolutionLevel(resolutionLevel);
        setTiles(tiles);
    }

    public MeshResolutionLevel getResolutionLevel() {
        return resolutionLevel;
    }

    public void setResolutionLevel(MeshResolutionLevel resolutionLevel) {
        if (resolutionLevel == null) {
            throw new IllegalArgumentException("resolutionLevel 不能为空");
        }
        this.resolutionLevel = resolutionLevel;
    }

    public List<SurfaceTile> getTiles() {
        return Collections.unmodifiableList(tiles);
    }

    public void setTiles(List<SurfaceTile> tiles) {
        if (tiles == null) {
            this.tiles = new ArrayList<>();
            return;
        }
        this.tiles = new ArrayList<>(tiles);
    }

    public SurfaceTile getTileById(String tileId) {
        if (tileId == null) {
            return null;
        }
        for (SurfaceTile t : tiles) {
            if (tileId.equals(t.getTileId())) {
                return t;
            }
        }
        return null;
    }

    public List<String> getNeighborTileIds(String tileId) {
        SurfaceTile t = getTileById(tileId);
        if (t == null) {
            return List.of();
        }
        return t.getNeighborTileIds();
    }

    public SurfaceTile findTileByDirection(Vector2 direction) {
        if (tiles.isEmpty()) {
            return null;
        }

        SurfaceTile fallback = pickSmallestTileId();
        if (direction == null) {
            return fallback;
        }

        float dx = direction.x;
        float dy = direction.y;
        float dLenSq = dx * dx + dy * dy;
        if (!(dLenSq > 0.0f) || Float.isNaN(dLenSq) || Float.isInfinite(dLenSq)) {
            return fallback;
        }

        float invLen = (float) (1.0 / Math.sqrt(dLenSq));
        float ndx = dx * invLen;
        float ndy = dy * invLen;

        float bestScore = -Float.MAX_VALUE;
        SurfaceTile best = null;
        String bestId = null;
        for (SurfaceTile t : tiles) {
            if (t == null) {
                continue;
            }
            Vector2 td = t.getDirection();
            if (td == null) {
                continue;
            }
            float tx = td.x;
            float ty = td.y;
            float tLenSq = tx * tx + ty * ty;
            if (!(tLenSq > 0.0f) || Float.isNaN(tLenSq) || Float.isInfinite(tLenSq)) {
                continue;
            }
            float tInvLen = (float) (1.0 / Math.sqrt(tLenSq));
            float ntx = tx * tInvLen;
            float nty = ty * tInvLen;
            float score = ndx * ntx + ndy * nty;

            String id = t.getTileId();
            if (best == null) {
                best = t;
                bestId = id;
                bestScore = score;
                continue;
            }

            if (score > bestScore) {
                best = t;
                bestId = id;
                bestScore = score;
            } else if (score == bestScore) {
                if (id != null && bestId != null && id.compareTo(bestId) < 0) {
                    best = t;
                    bestId = id;
                }
            }
        }

        return best != null ? best : fallback;
    }

    private SurfaceTile pickSmallestTileId() {
        SurfaceTile best = null;
        String bestId = null;
        for (SurfaceTile t : tiles) {
            if (t == null || t.getTileId() == null) {
                continue;
            }
            if (best == null) {
                best = t;
                bestId = t.getTileId();
                continue;
            }
            if (t.getTileId().compareTo(bestId) < 0) {
                best = t;
                bestId = t.getTileId();
            }
        }
        return best != null ? best : (tiles.isEmpty() ? null : tiles.get(0));
    }
}
