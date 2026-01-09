package com.staraxis.game.client.world;

import com.staraxis.game.shared.net.worldgen.snapshot.SectorSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;
import com.staraxis.game.shared.world.HexCoord;

import java.util.HashMap;
import java.util.Map;

/**
 * 适配器：将 shared 层的 UniverseSnapshot 转换为客户端的 UniverseModel。
 */
public class UniverseModelToWorldMapAdapter {

    public UniverseModel adapt(UniverseSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        Map<HexCoord, SectorModel> sectors = new HashMap<>();
        for (SectorSnapshot sectorSnapshot : snapshot.getSectors()) {
            HexCoord coord = HexCoord.of(
                    sectorSnapshot.getCoord().getX(),
                    sectorSnapshot.getCoord().getY(),
                    sectorSnapshot.getCoord().getZ()
            );

            SectorModel sectorModel = new SectorModel(
                    coord,
                    sectorSnapshot.getSectorType(),
                    sectorSnapshot.getStarSystem(),
                    sectorSnapshot.getWorldPositionXLy(),
                    sectorSnapshot.getWorldPositionYLy()
            );
            sectors.put(coord, sectorModel);
        }

        return new UniverseModel(
                snapshot.getSeedValue(),
                snapshot.getBoundsRadius(),
                sectors,
                snapshot.getStats()
        );
    }
}
