package com.staraxis.game.core.world.stellar.surface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.staraxis.game.shared.world.stellar.surface.PlanetSurfaceMesh;
import com.staraxis.game.shared.world.stellar.surface.SurfaceTile;

public final class SurfaceMeshValidator {

    private SurfaceMeshValidator() {
    }

    public static List<String> validateMesh(PlanetSurfaceMesh mesh) {
        List<String> errors = new ArrayList<>();
        if (mesh == null) {
            errors.add("mesh 不能为空");
            return errors;
        }
        if (mesh.getResolutionLevel() == null) {
            errors.add("resolutionLevel 不能为空");
        }

        List<SurfaceTile> tiles = mesh.getTiles();
        if (tiles == null) {
            errors.add("tiles 不能为空");
            return errors;
        }

        Map<String, SurfaceTile> byId = new HashMap<>();
        int pentCount = 0;
        for (SurfaceTile t : tiles) {
            if (t == null) {
                errors.add("tiles 中存在 null 元素");
                continue;
            }
            String id = t.getTileId();
            if (id == null || id.isBlank()) {
                errors.add("tileId 不能为空");
                continue;
            }
            if (byId.put(id, t) != null) {
                errors.add("tileId 重复: " + id);
            }
            if (t.isPentagon()) {
                pentCount++;
            }
        }

        if (pentCount != 12) {
            errors.add("五边形数量必须为 12，实际为 " + pentCount);
        }

        for (Map.Entry<String, SurfaceTile> e : byId.entrySet()) {
            String id = e.getKey();
            SurfaceTile t = e.getValue();
            List<String> neighbors = t.getNeighborTileIds();
            if (neighbors == null) {
                errors.add("neighborTileIds 不能为空: " + id);
                continue;
            }
            Set<String> uniq = new HashSet<>();
            for (String nb : neighbors) {
                if (nb == null || nb.isBlank()) {
                    errors.add("neighborTileIds 含空值: " + id);
                    continue;
                }
                if (id.equals(nb)) {
                    errors.add("不允许自邻接: " + id);
                    continue;
                }
                if (!uniq.add(nb)) {
                    errors.add("neighborTileIds 重复: " + id + " -> " + nb);
                }
                SurfaceTile back = byId.get(nb);
                if (back == null) {
                    errors.add("neighborTileIds 引用不存在的 tileId: " + id + " -> " + nb);
                    continue;
                }
                List<String> backNeighbors = back.getNeighborTileIds();
                if (backNeighbors == null || !backNeighbors.contains(id)) {
                    errors.add("邻接关系非双向一致: " + id + " <-> " + nb);
                }
            }
        }

        return errors;
    }

    public static void requireValid(PlanetSurfaceMesh mesh) {
        List<String> errors = validateMesh(mesh);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }
}
