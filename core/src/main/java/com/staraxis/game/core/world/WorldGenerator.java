package com.staraxis.game.core.world;

import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;

/**
 * 世界生成器接口 (World Generator Interface). 负责根据配置生成世界地图。
 */
public interface WorldGenerator {

    /**
     * 根据给定配置生成世界地图。
     *
     * @param config 生成配置
     * @return 生成的世界地图
     */
    WorldMap generate(WorldGenConfig config);
}
