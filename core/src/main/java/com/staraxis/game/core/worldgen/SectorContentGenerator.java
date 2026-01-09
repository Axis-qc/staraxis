package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;

/**
 * 星区内容生成器接口（SectorContentGenerator）。
 * 
 * 根据星区类型ID实际生成星区内容（恒星系、星云、资源点等）。
 * 
 * 使用的接口: WorldGenConfig, HexTile
 * 提供的接口: 为 HexSectorUniverseGenerator 提供内容生成功能
 */
public interface SectorContentGenerator {

    /**
     * 检查是否支持生成指定类型的内容。
     * 
     * @param sectorTypeId 星区类型ID
     * @return 是否支持
     */
    boolean supports(String sectorTypeId);

    /**
     * 为星区生成内容。
     * 
     * @param tile 星区瓦片（已分配类型ID）
     * @param config 生成配置
     * @param seedValue 随机种子
     */
    void generateContent(HexTile tile, WorldGenConfig config, long seedValue);
}
