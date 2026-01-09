package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;

/**
 * 星云内容生成器（NebulaContentGenerator）。
 * 
 * 根据星区类型ID为 nebula 类型的星区实际生成星云内容。
 * 当前为占位实现，后续可扩展星云特性。
 * 
 * 使用的接口: WorldGenConfig, HexTile
 * 提供的接口: 为 HexSectorUniverseGenerator 提供星云内容生成功能
 */
public class NebulaContentGenerator implements SectorContentGenerator {

    @Override
    public boolean supports(String sectorTypeId) {
        return SectorTypes.NEBULA.equals(sectorTypeId);
    }

    @Override
    public void generateContent(HexTile tile, WorldGenConfig config, long seedValue) {
        if (!supports(tile.getTypeId())) {
            return;
        }

        // TODO: 实现星云内容生成（星云类型、资源点等）
        // 当前仅标记类型，不生成额外内容
    }
}
