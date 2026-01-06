package com.staraxis.game.shared.world.stellar;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 世界生成统计（WorldGenStats）。
 *
 * 作用（Purpose）：提供回归验证所需的最小统计集合（FR-008 / SC-003）。 依赖（Dependencies）：仅 Java 标准库。
 * 对外接口（Public API）：各字段的 getter/setter。
 */
public class WorldGenStats implements Serializable {

    private int tileCount; // tileCount（总格子数量）
    private Map<String, Integer> sectorCounts; // sectorCounts（按 sectorTypeId 计数：galaxy/deep_space/nebula）
    private int galaxyTileCount; // galaxyTileCount（星系区块数量）
    private int starCount; // starCount（恒星总数）
    private int planetCount; // planetCount（行星总数）
    private String starsPerSystemMinMax; // starsPerSystemMinMax（可选日志字段，如 "min=1,max=3"）

    public WorldGenStats() {
        this.sectorCounts = new LinkedHashMap<>();
    }

    public int getTileCount() {
        return tileCount;
    }

    public void setTileCount(int tileCount) {
        this.tileCount = tileCount;
    }

    public Map<String, Integer> getSectorCounts() {
        return Collections.unmodifiableMap(sectorCounts);
    }

    public void setSectorCounts(Map<String, Integer> sectorCounts) {
        if (sectorCounts == null) {
            this.sectorCounts = new LinkedHashMap<>();
            return;
        }
        this.sectorCounts = new LinkedHashMap<>(sectorCounts);
    }

    public int getGalaxyTileCount() {
        return galaxyTileCount;
    }

    public void setGalaxyTileCount(int galaxyTileCount) {
        this.galaxyTileCount = galaxyTileCount;
    }

    public int getStarCount() {
        return starCount;
    }

    public void setStarCount(int starCount) {
        this.starCount = starCount;
    }

    public int getPlanetCount() {
        return planetCount;
    }

    public void setPlanetCount(int planetCount) {
        this.planetCount = planetCount;
    }

    public String getStarsPerSystemMinMax() {
        return starsPerSystemMinMax;
    }

    public void setStarsPerSystemMinMax(String starsPerSystemMinMax) {
        this.starsPerSystemMinMax = starsPerSystemMinMax;
    }
}
