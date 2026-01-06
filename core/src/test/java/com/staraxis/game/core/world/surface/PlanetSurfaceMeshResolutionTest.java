package com.staraxis.game.core.world.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.stellar.surface.PlanetSurfaceMeshGenerator;
import com.staraxis.game.shared.model.Vector2;
import com.staraxis.game.shared.world.stellar.surface.MeshResolutionLevel;
import com.staraxis.game.shared.world.stellar.surface.PlanetSurfaceMesh;
import com.staraxis.game.shared.world.stellar.surface.SurfaceTile;

public class PlanetSurfaceMeshResolutionTest {

    @Test
    void resolution_tile_count_increases_low_to_high() {
        PlanetSurfaceMeshGenerator generator = new PlanetSurfaceMeshGenerator();

        PlanetSurfaceMesh low = generator.generate(MeshResolutionLevel.LOW);
        PlanetSurfaceMesh medium = generator.generate(MeshResolutionLevel.MEDIUM);
        PlanetSurfaceMesh high = generator.generate(MeshResolutionLevel.HIGH);

        assertTrue(low.getTiles().size() < medium.getTiles().size());
        assertTrue(medium.getTiles().size() < high.getTiles().size());
    }

    @Test
    void same_resolution_is_deterministic_and_direction_query_is_stable() {
        PlanetSurfaceMeshGenerator generator = new PlanetSurfaceMeshGenerator();

        PlanetSurfaceMesh a = generator.generate(MeshResolutionLevel.MEDIUM);
        PlanetSurfaceMesh b = generator.generate(MeshResolutionLevel.MEDIUM);

        assertEquals(digest(a), digest(b));

        SurfaceTile ta = a.findTileByDirection(new Vector2(1, 0));
        SurfaceTile tb = b.findTileByDirection(new Vector2(1, 0));
        assertEquals(ta.getTileId(), tb.getTileId());
    }

    private static String digest(PlanetSurfaceMesh mesh) {
        StringBuilder sb = new StringBuilder();
        sb.append(mesh.getResolutionLevel()).append('|').append(mesh.getTiles().size()).append('|');
        int limit = Math.min(50, mesh.getTiles().size());
        List<SurfaceTile> tiles = mesh.getTiles();
        for (int i = 0; i < limit; i++) {
            SurfaceTile t = tiles.get(i);
            sb.append(t.getTileId()).append(':').append(t.isPentagon() ? 'p' : 'h').append(':').append(t.getNeighborTileIds().size()).append(';');
        }
        return sb.toString();
    }
}
