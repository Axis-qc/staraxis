package com.staraxis.universegen;

import com.staraxis.universegen.model.Sector;

/**
 * SectorLocatorService 用于根据 Sector 对象或 sectorId 计算其在银河坐标中的中心点坐标（公里）。
 * 
 * 约定：
 * 1. Sector 采用六边形网格，边长由 UniverseGenConfig#getSectorEdgeLy() 决定，单位光年；
 * 2. galaxy-level 坐标系使用公里（km）。
 * 3. sectorId 按照轴向坐标 (q,r) 经过 Cantor Pairing 编码为 32-bit 或 64-bit 整数；
 *    目前简化：sectorId = q << 32 | (r & 0xffffffff)。
 * 4. 解析时再拆分回 (q,r)。
 */
public final class SectorLocatorService {

    private final double sectorEdgeLy; // sector 六边形边长，光年
    private final double LY_TO_KM = 9.4607e12; // 光年到公里的常量（简化值）

    public SectorLocatorService(double sectorEdgeLy) {
        this.sectorEdgeLy = sectorEdgeLy;
    }

    public CoordinateSystem locateCenter(Sector sector) {
        return locateCenter(sector.id());
    }

    /**
     * 根据 sectorId 返回其中心点在银河坐标系下的 CoordinateSystem。
     */
    public CoordinateSystem locateCenter(long sectorId) {
        long q = sectorId >> 32;
        long r = sectorId & 0xffffffffL;
        double[] xy = axialToWorld(q, r);
        return new CoordinateSystem(sectorId, xy[0], xy[1], 0);
    }

    /**
     * 轴向坐标 (q,r) → 世界坐标 (xKm, yKm)。
     * 公式来自常见六边形网格映射。
     */
    private double[] axialToWorld(long q, long r) {
        // 单位光年 → 公里再乘边长
        double sizeKm = sectorEdgeLy * LY_TO_KM;
        double x = sizeKm * (Math.sqrt(3) * q + Math.sqrt(3)/2 * r);
        double y = sizeKm * (3.0/2.0 * r);
        return new double[]{x, y};
    }
}
