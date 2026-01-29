package staraxis.game.world;

import staraxis.game.world.hex.HexMath;
import staraxis.game.world.hex.SectorCoord;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorldGenerator
 *
 * 纯逻辑世界生成器：按六边形半径生成星区，并拼合为大地图。
 */
public final class WorldGenerator {

    private WorldGenerator() {
    }

    public static WorldMap generate(WorldGenConfig cfg) {
        if (cfg == null) {
            throw new IllegalArgumentException("worldGenConfig_required");
        }
        if (cfg.worldRadius < 0) {
            throw new IllegalArgumentException("worldRadius_invalid");
        }

        int radius = cfg.worldRadius;
        SectorCoord origin = new SectorCoord(0, 0);

        Map<SectorCoord, WorldSector> sectors = new LinkedHashMap<>();

        // axial 坐标的半径盘：distance((0,0),(q,r)) <= radius
        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                SectorCoord c = new SectorCoord(q, r);
                if (HexMath.distance(origin, c) <= radius) {
                    Vec2d center = WorldHexLayout.sectorCenterWorld2D_GU(c);
                    WorldSector s = new WorldSector(c, center);
                    sectors.put(c, s);
                }
            }
        }

        WorldMap worldMap = new WorldMap(radius, cfg.playerNationId, sectors);

        // 预留：国家占位逻辑（当前仅占位入口，不做算法）
        // - 未来：可以根据 cfg.playerNationId / 多国家列表做出生点分配
        return worldMap;
    }
}
