package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldGenStatsSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 将服务端生成的星区/恒星系数据映射为新版世界快照（UniverseSnapshot）。
 * 
 * 当前为“最小可用”版本：
 * - 负责填充星区坐标与 sectorType
 * - 当 sectorType=galaxy 时填充一个最简 StarSystemSnapshot（stars 列表存在但字段可简化）
 */
public class UniverseSnapshotMapper {

    public UniverseSnapshot createEmpty(long seedValue, int boundsRadius) {
        UniverseSnapshot snapshot = new UniverseSnapshot();
        snapshot.setSeedValue(seedValue);
        snapshot.setBoundsRadius(boundsRadius);
        return snapshot;
    }

    public void setStats(UniverseSnapshot snapshot,
                         int sectorCount,
                         int galaxyCount,
                         int starCount,
                         int planetCount) {
        WorldGenStatsSnapshot stats = new WorldGenStatsSnapshot();
        stats.setTileCount(sectorCount);
        stats.setGalaxyTileCount(galaxyCount);
        stats.setStarCount(starCount);
        stats.setPlanetCount(planetCount);
        snapshot.setStats(stats);
    }

    public void setSectors(UniverseSnapshot snapshot, List<SectorSnapshot> sectors) {
        snapshot.setSectors(sectors);
    }

    public SectorSnapshot sector(int x, int y, int z, String sectorType, StarSystemSnapshot starSystem, double worldX, double worldY) {
        SectorSnapshot s = new SectorSnapshot();
        HexCoordSnapshot coord = new HexCoordSnapshot();
        coord.setX(x);
        coord.setY(y);
        coord.setZ(z);
        s.setCoord(coord);
        s.setSectorType(sectorType);
        s.setStarSystem(starSystem);
        s.setWorldPositionLy(worldX, worldY);
        return s;
    }

    /**
     * 构造一个最简恒星系快照：
     * - systemId 用于调试
     * - stars 仅提供数量占位（id/type 可留空）
     */
    public StarSystemSnapshot minimalStarSystem(String systemId, int starCount) {
        StarSystemSnapshot sys = new StarSystemSnapshot();
        sys.setId(systemId);

        List<StarSnapshot> stars = new ArrayList<>();
        for (int i = 0; i < starCount; i++) {
            StarSnapshot star = new StarSnapshot();
            star.setId(systemId + "-star-" + i);
            star.setStarTypeId("unknown");
            // planets 暂不填充（最小可用版本）
            stars.add(star);
        }
        sys.setStars(stars);
        return sys;
    }

    public String normalizeSectorType(String raw) {
        if (raw == null) {
            return SectorTypes.DEEP_SPACE;
        }
        return switch (raw) {
            case SectorTypes.STAR_SYSTEM -> SectorTypes.STAR_SYSTEM;
            case SectorTypes.NEBULA -> SectorTypes.NEBULA;
            case SectorTypes.DEEP_SPACE -> SectorTypes.DEEP_SPACE;
            default -> SectorTypes.DEEP_SPACE;
        };
    }
}
