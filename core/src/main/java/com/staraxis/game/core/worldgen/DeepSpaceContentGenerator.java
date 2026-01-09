package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;

/**
 * 深空内容生成器（DeepSpaceContentGenerator）。
 * 
 * 根据星区类型ID为 deep-space 类型的星区实际生成深空内容。
 * 当前为占位实现，后续可扩展深空资源点等。
 * 
 * 使用的接口: WorldGenConfig, HexTile
 * 提供的接口: 为 HexSectorUniverseGenerator 提供深空内容生成功能
 */
public class DeepSpaceContentGenerator implements SectorContentGenerator {

    @Override
    public boolean supports(String sectorTypeId) {
        return SectorTypes.DEEP_SPACE.equals(sectorTypeId);
    }

    @Override
    public void generateContent(HexTile tile, WorldGenConfig config, long seedValue) {
        if (!supports(tile.getTypeId())) {
            return;
        }

        // TODO: 实现深空内容生成（资源点等）
        // 当前仅标记类型，不生成额外内容
    }
}
