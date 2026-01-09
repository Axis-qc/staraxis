package com.staraxis.game.shared.net.worldgen.snapshot;

/**
 * 星区快照（SectorSnapshot）：对应一个六边形星区。
 */
public class SectorSnapshot {

    private HexCoordSnapshot coord;

    /**
     * 星区类型（字符串 ID，见 {@link SectorTypes}）：
     * - star-system: 星系星区（会生成一个恒星系 Star System）
     * - nebula: 星云
     * - deep_space: 深空
     */
    private String sectorType;

    /**
     * 当 sectorType=star-system 时存在。
     */
    private StarSystemSnapshot starSystem;

    /** 星区中心点的物理世界X坐标（单位：光年） */
    private double worldPositionXLy;

    /** 星区中心点的物理世界Y坐标（单位：光年） */
    private double worldPositionYLy;

    /** 调试用：占用来源（preset/allocated） */
    private String occupancySource;

    public SectorSnapshot() {
    }

    public HexCoordSnapshot getCoord() {
        return coord;
    }

    public void setCoord(HexCoordSnapshot coord) {
        this.coord = coord;
    }

    public String getSectorType() {
        return sectorType;
    }

    public void setSectorType(String sectorType) {
        this.sectorType = sectorType;
    }

    public StarSystemSnapshot getStarSystem() {
        return starSystem;
    }

    public void setStarSystem(StarSystemSnapshot starSystem) {
        this.starSystem = starSystem;
    }

    /**
     * 获取星区中心点的物理世界X坐标（光年）
     */
    public double getWorldPositionXLy() {
        return worldPositionXLy;
    }

    /**
     * 设置星区中心点的物理世界X坐标（光年）
     */
    public void setWorldPositionXLy(double worldPositionXLy) {
        this.worldPositionXLy = worldPositionXLy;
    }

    /**
     * 获取星区中心点的物理世界Y坐标（光年）
     */
    public double getWorldPositionYLy() {
        return worldPositionYLy;
    }

    /**
     * 设置星区中心点的物理世界Y坐标（光年）
     */
    public void setWorldPositionYLy(double worldPositionYLy) {
        this.worldPositionYLy = worldPositionYLy;
    }

    /**
     * 获取星区中心点的物理世界坐标（光年）
     * @return 包含 [x, y] 坐标的数组
     */
    public double[] getWorldPositionLy() {
        return new double[]{worldPositionXLy, worldPositionYLy};
    }

    /**
     * 设置星区中心点的物理世界坐标（光年）
     * @param x 世界X坐标（光年）
     * @param y 世界Y坐标（光年）
     */
    public void setWorldPositionLy(double x, double y) {
        this.worldPositionXLy = x;
        this.worldPositionYLy = y;
    }

    public String getOccupancySource() {
        return occupancySource;
    }

    public void setOccupancySource(String occupancySource) {
        this.occupancySource = occupancySource;
    }
}