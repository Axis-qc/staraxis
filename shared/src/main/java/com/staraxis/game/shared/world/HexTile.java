package com.staraxis.game.shared.world;

import java.io.Serializable;

import com.staraxis.game.shared.world.astronomical.SectorSizeDefinition;
import com.staraxis.game.shared.world.stellar.StarSystem;

/**
 * 六边形瓦片数据模型 (Hexagonal tile data model).
 * 
 * 注意：星区大小由 SectorSizeDefinition 定义，默认 1 光年 = 63,241 AU。
 * 使用 AstronomicalUnitSystem.getSectorSizeDefinition() 获取星区大小定义。
 */
public class HexTile implements Serializable {

    private final HexCoord coord;
    private String typeId;
    private boolean hasHabitable;
    private StarSystem starSystem;

    public HexTile(HexCoord coord, String typeId) {
        this.coord = coord;
        this.typeId = typeId;
        this.hasHabitable = false;
    }

    public HexCoord getCoord() {
        return coord;
    }

    public String getTypeId() {
        return typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public boolean isHasHabitable() {
        return hasHabitable;
    }

    public void setHasHabitable(boolean hasHabitable) {
        this.hasHabitable = hasHabitable;
    }

    public StarSystem getStarSystem() {
        return starSystem;
    }

    public void setStarSystem(StarSystem starSystem) {
        this.starSystem = starSystem;
    }

    /**
     * 获取星区大小（基于 SectorSizeDefinition）。
     * 
     * @param sectorSizeDefinition 星区大小定义
     * @return 星区大小（AU）
     */
    public com.staraxis.game.shared.world.astronomical.AstronomicalUnit getSectorSize(
            SectorSizeDefinition sectorSizeDefinition) {
        if (sectorSizeDefinition == null) {
            throw new IllegalArgumentException("星区大小定义不能为空");
        }
        return sectorSizeDefinition.getSizeInAU();
    }

    /**
     * 获取星区大小（光年）。
     * 
     * @param sectorSizeDefinition 星区大小定义
     * @return 星区大小（光年）
     */
    public double getSectorSizeInLightYears(SectorSizeDefinition sectorSizeDefinition) {
        if (sectorSizeDefinition == null) {
            throw new IllegalArgumentException("星区大小定义不能为空");
        }
        return sectorSizeDefinition.getSizeInLightYears();
    }

    @Override
    public String toString() {
        return "HexTile{"
                + "coord=" + coord
                + ", typeId='" + typeId + '\''
                + ", hasHabitable=" + hasHabitable
                + ", starSystem=" + starSystem
                + '}';
    }
}
