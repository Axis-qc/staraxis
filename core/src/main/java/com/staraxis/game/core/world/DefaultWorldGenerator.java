package com.staraxis.game.core.world;

import com.badlogic.gdx.Gdx;
import com.staraxis.game.shared.world.*;

import java.util.Random;

/**
 * 默认世界生成器实现 (Default world generator implementation). 保证在相同种子和配置下生成确定的地图。
 */
public class DefaultWorldGenerator implements WorldGenerator {

    @Override
    public WorldMap generate(WorldGenConfig config) {
        long startTime = System.currentTimeMillis();
        int radius = WorldGenDefinitions.getRadius(config.getMapSizePresetId());
        WorldMap worldMap = new WorldMap(config, radius);

        // 使用配置中的 seedValue 初始化随机数生成器，保证确定性 (T028)
        Random random = new Random(config.getSeedValue());

        // 遍历六边形范围生成瓦片 (T025)
        int tileCount = 0;
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                HexCoord coord = HexCoord.of(q, -q - r, r);
                HexTile tile = generateTile(coord, random, config.getHabitableRatio());
                worldMap.addTile(tile);
                tileCount++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        if (Gdx.app != null) {
            Gdx.app.log("WorldGen", String.format("Generated world: radius=%d, tiles=%d, duration=%dms, seed=%d",
                    radius, tileCount, duration, config.getSeedValue()));
        }

        return worldMap;
    }

    /**
     * 生成单个瓦片并分配类型 (T026, T027)
     */
    private HexTile generateTile(HexCoord coord, Random random, float habitableRatio) {
        // 简单的随机分配策略 (T026)
        // 0-0.6: galaxy, 0.6-0.8: deep_space, 0.8-1.0: nebula
        float typeRoll = random.nextFloat();
        String typeId;
        if (typeRoll < 0.6f) {
            typeId = "galaxy";
        } else if (typeRoll < 0.8f) {
            typeId = "deep_space";
        } else {
            typeId = "nebula";
        }

        HexTile tile = new HexTile(coord, typeId);

        // 仅在 galaxy 类型的瓦片中根据概率生成宜居星球 (T027)
        if ("galaxy".equals(typeId)) {
            if (random.nextFloat() < habitableRatio) {
                tile.setHasHabitable(true);
            }
        }

        return tile;
    }
}
