package com.staraxis.game.client.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.staraxis.game.shared.world.HexCoord;

/**
 * 客户端运行时世界模型（UniverseModel）。
 * 
 * 说明：
 * - 仅用于渲染与交互，不承载服务端生成逻辑。
 * - key 为 HexCoord，value 为 SectorModel。
 */
public class UniverseModel {

    private final long seedValue;
    private final int boundsRadius;
    private final Map<HexCoord, SectorModel> sectors;

    public UniverseModel(long seedValue, int boundsRadius, Map<HexCoord, SectorModel> sectors) {
        this.seedValue = seedValue;
        this.boundsRadius = boundsRadius;
        this.sectors = sectors != null ? new HashMap<>(sectors) : new HashMap<>();
    }

    public long getSeedValue() {
        return seedValue;
    }

    public int getBoundsRadius() {
        return boundsRadius;
    }

    public Map<HexCoord, SectorModel> getSectors() {
        return Collections.unmodifiableMap(sectors);
    }

    public SectorModel getSector(HexCoord coord) {
        return sectors.get(coord);
    }
}
