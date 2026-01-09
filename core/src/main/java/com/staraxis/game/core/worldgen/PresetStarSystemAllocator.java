package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;

import java.util.*;

/**
 * 预设恒星系分配器（PresetStarSystemAllocator）。
 * 
 * 负责在星区中预设恒星系位置（固定位置或随机位置）。
 * 只分配星区类型ID，不实际生成恒星系内容。
 * 
 * 使用的接口: 无
 * 提供的接口: 为 HexSectorUniverseGenerator 提供预设恒星系分配功能
 */
public class PresetStarSystemAllocator {

    /**
     * 分配预设恒星系位置。
     * 
     * @param tiles 所有星区瓦片
     * @param seedValue 随机种子
     * @param fixedPositions 固定位置列表（可为空）
     * @param randomCount 随机位置数量（如果 fixedPositions 为空或数量不足，则随机分配）
     * @return 已分配恒星系类型的瓦片集合
     */
    public Set<HexCoord> allocatePresetStarSystems(
            Map<HexCoord, HexTile> tiles,
            long seedValue,
            List<HexCoord> fixedPositions,
            int randomCount) {
        
        Set<HexCoord> allocated = new HashSet<>();
        Random rng = new Random(seedValue);
        
        // 1. 先分配固定位置
        if (fixedPositions != null && !fixedPositions.isEmpty()) {
            for (HexCoord fixedPos : fixedPositions) {
                HexTile tile = tiles.get(fixedPos);
                if (tile != null && !allocated.contains(fixedPos)) {
                    tile.setTypeId(SectorTypes.STAR_SYSTEM);
                    allocated.add(fixedPos);
                }
            }
        }
        
        // 2. 随机分配剩余位置
        List<HexCoord> availableCoords = new ArrayList<>();
        for (HexCoord coord : tiles.keySet()) {
            if (!allocated.contains(coord)) {
                availableCoords.add(coord);
            }
        }
        
        int remainingCount = randomCount - allocated.size();
        if (remainingCount > 0 && !availableCoords.isEmpty()) {
            Collections.shuffle(availableCoords, rng);
            int count = Math.min(remainingCount, availableCoords.size());
            for (int i = 0; i < count; i++) {
                HexCoord coord = availableCoords.get(i);
                HexTile tile = tiles.get(coord);
                if (tile != null) {
                    tile.setTypeId(SectorTypes.STAR_SYSTEM);
                    allocated.add(coord);
                }
            }
        }
        
        return allocated;
    }
}
