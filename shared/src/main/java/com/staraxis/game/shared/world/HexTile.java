package com.staraxis.game.shared.world;

import java.io.Serializable;

import com.staraxis.game.shared.world.stellar.StarSystem;

import java.io.Serializable;

/**
 * 六边形瓦片数据模型 (Hexagonal tile data model).
 * 代表一个星区，包含其六边形坐标、物理世界坐标和内容。
 */
public class HexTile implements Serializable {

    private final HexCoord coord; // 六边形坐标
    private final double[] worldPosition; // 世界坐标中心点 [x, y] (单位：光年)
    private String typeId;
    private boolean hasHabitable;
    private StarSystem starSystem;

    /**
     * 构造一个新的六边形瓦片。
     *
     * @param coord 六边形坐标
     * @param typeId 星区类型ID
     * @param converter 坐标转换器，用于计算物理位置
     */
    public HexTile(HexCoord coord, String typeId, HexCoordinateConverter converter) {
        if (coord == null || converter == null) {
            throw new IllegalArgumentException("坐标和转换器不能为空");
        }
        this.coord = coord;
        this.typeId = typeId;
        this.worldPosition = converter.hexToWorld(coord);
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
     * 获取星区的物理世界坐标中心点。
     *
     * @return 世界坐标 [x, y] (单位：光年)
     */
    public double[] getWorldPosition() {
        return worldPosition;
    }

    @Override
    public String toString() {
        return "HexTile{"
                + "coord=" + coord
                + ", worldPosition=[" + worldPosition[0] + ", " + worldPosition[1] + "]"
                + ", typeId='" + typeId + '\''
                + ", hasHabitable=" + hasHabitable
                + ", starSystem=" + starSystem
                + '}';
    }
}
