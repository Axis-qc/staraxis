package com.staraxis.game.client.world;

import java.util.HashMap;
import java.util.Map;

import com.staraxis.game.shared.net.worldgen.snapshot.SectorSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;
import com.staraxis.game.shared.world.HexCoord;

/**
 * UniverseSnapshot -> UniverseModel 转换器（客户端侧）。
 */
public class UniverseSnapshotConverter {

    public UniverseModel toUniverseModel(UniverseSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot 不能为空");
        }

        Map<HexCoord, SectorModel> sectors = new HashMap<>();
        for (SectorSnapshot s : snapshot.getSectors()) {
            if (s == null || s.getCoord() == null) {
                continue;
            }
            HexCoord coord = HexCoord.of(s.getCoord().getX(), s.getCoord().getY(), s.getCoord().getZ());
            sectors.put(coord, new SectorModel(coord, s.getSectorType(), s.getStarSystem()));
        }

        return new UniverseModel(snapshot.getSeedValue(), snapshot.getBoundsRadius(), sectors);
    }
}
