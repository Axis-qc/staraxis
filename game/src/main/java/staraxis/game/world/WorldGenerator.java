package staraxis.game.world;

import staraxis.game.world.hex.SectorCoord;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorldGenerator。
 *
 * 为 3D 宇宙场景生成最小化的世界地图。
 * 所有恒星系位于 sector (0,0) 附近（星系大小远小于 sector 尺寸）。
 */
public final class WorldGenerator {

    private WorldGenerator() {
    }

    public static WorldMap generate(WorldGenConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("worldGenConfig_required");
        }

        // 最小半径 1，确保至少包含 (0,0) 星区
        int radius = 1;
        SectorCoord origin = new SectorCoord(0, 0);

        Map<SectorCoord, WorldSector> sectors = new LinkedHashMap<>();

        // 只生成中心星区 (0,0)
        Vec2d center = WorldHexLayout.sectorCenterWorld2D_GU(origin);
        WorldSector s = new WorldSector(origin, center);
        s.ownerNationId = null;
        sectors.put(origin, s);

        WorldMap worldMap = new WorldMap(radius, cfg.playerNationDef == null ? null : cfg.playerNationDef.id, sectors);

        return worldMap;
    }
}
