package com.staraxis.game.core.world.stellar.surface;

import java.util.ArrayList;
import java.util.List;

import com.staraxis.game.shared.model.Vector2;
import com.staraxis.game.shared.world.stellar.surface.MeshResolutionLevel;
import com.staraxis.game.shared.world.stellar.surface.PlanetSurfaceMesh;
import com.staraxis.game.shared.world.stellar.surface.SurfaceTile;

public class PlanetSurfaceMeshGenerator {

    public PlanetSurfaceMesh generate(MeshResolutionLevel resolutionLevel) {
        if (resolutionLevel == null) {
            throw new IllegalArgumentException("resolutionLevel 不能为空");
        }

        int hexCount = hexCountFor(resolutionLevel);
        int total = 12 + hexCount;

        List<SurfaceTile> tiles = new ArrayList<>(total);
        for (int i = 0; i < 12; i++) {
            SurfaceTile t = new SurfaceTile();
            t.setTileId(pentId(i));
            t.setPentagon(true);
            tiles.add(t);
        }
        for (int i = 0; i < hexCount; i++) {
            SurfaceTile t = new SurfaceTile();
            t.setTileId(hexId(i));
            t.setPentagon(false);
            tiles.add(t);
        }

        for (int i = 0; i < total; i++) {
            SurfaceTile t = tiles.get(i);
            int prev = (i + total - 1) % total;
            int next = (i + 1) % total;
            List<String> neighbors = List.of(tiles.get(prev).getTileId(), tiles.get(next).getTileId());
            t.setNeighborTileIds(neighbors);

            double ang = (Math.PI * 2.0) * ((double) i / (double) total);
            t.setDirection(new Vector2((float) Math.cos(ang), (float) Math.sin(ang)));
        }

        return new PlanetSurfaceMesh(resolutionLevel, tiles);
    }

    private static int hexCountFor(MeshResolutionLevel level) {
        return switch (level) {
            case LOW ->
                20;
            case MEDIUM ->
                80;
            case HIGH ->
                320;
        };
    }

    private static String pentId(int i) {
        return String.format("p%03d", i);
    }

    private static String hexId(int i) {
        return String.format("h%03d", i);
    }
}
