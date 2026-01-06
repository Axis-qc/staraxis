package com.staraxis.game.core.world.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.staraxis.game.core.world.stellar.surface.PlanetSurfaceMeshGenerator;
import com.staraxis.game.core.world.stellar.surface.SurfaceMeshValidator;
import com.staraxis.game.shared.world.stellar.surface.MeshResolutionLevel;
import com.staraxis.game.shared.world.stellar.surface.PlanetSurfaceMesh;
import com.staraxis.game.shared.world.stellar.surface.SurfaceTile;

public class PlanetSurfaceMeshTopologyTest {

    @Test
    void topology_valid_mesh_has_12_pentagons_and_bidirectional_neighbors() {
        PlanetSurfaceMeshGenerator generator = new PlanetSurfaceMeshGenerator();
        PlanetSurfaceMesh mesh = generator.generate(MeshResolutionLevel.LOW);

        List<String> errors = SurfaceMeshValidator.validateMesh(mesh);
        assertTrue(errors.isEmpty(), String.join("; ", errors));

        int pentCount = 0;
        for (SurfaceTile t : mesh.getTiles()) {
            if (t.isPentagon()) {
                pentCount++;
            }
        }
        assertEquals(12, pentCount);
    }
}
