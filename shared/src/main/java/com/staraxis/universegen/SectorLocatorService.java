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
import java.util.ArrayList;
import java.util.List;

public final class SectorLocatorService {

    private final double sectorEdgeLy; // sector 六边形边长，光年
    private static final double LY_TO_KM = 9_460_730_472_580.8; // 1ly=9460730472580.8km（标准值，用于真实比例与可验收）

    public SectorLocatorService(double sectorEdgeLy) {
        this.sectorEdgeLy = sectorEdgeLy;
    }

    public CoordinateSystem locateCenter(Sector sector) {
        return locateCenter(sector.id());
    }

    /**
     * 按六边形半径 R（圈数）生成轴向坐标 (q,r) 对应的 sectorId 列表。
     * 
     * 约定：
     * - R=0 时仅包含 (0,0)
     * - 采用 axial 坐标，范围满足 hex distance <= R
     */
    public List<Long> generateSectorIdsByRadius(int radiusR) {
        if (radiusR < 0) {
            throw new IllegalArgumentException("radiusR 必须 >= 0");
        }
        List<Long> ids = new ArrayList<>(1 + 3 * radiusR * (radiusR + 1));
        for (int q = -radiusR; q <= radiusR; q++) {
            int rMin = Math.max(-radiusR, -q - radiusR);
            int rMax = Math.min(radiusR, -q + radiusR);
            for (int r = rMin; r <= rMax; r++) {
                ids.add(packAxialToSectorId(q, r));
            }
        }
        return ids;
    }

    /**
     * 将 axial 坐标(q,r)打包为 sectorId。
     */
    public static long packAxialToSectorId(int q, int r) {
        return (((long) q) << 32) | (r & 0xffffffffL);
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
        // 这里的 sizeKm 是“六边形边长（edge length）”换算到 km 的长度。
        // 对于 pointy-top axial 坐标系，相邻中心距 = sqrt(3) * edgeLength。
        // 该公式用于在后续验收中验证 1ly 真实比例（SC-002）。
        double edgeLengthKm = sectorEdgeLy * LY_TO_KM;

        double x = edgeLengthKm * (Math.sqrt(3) * q + Math.sqrt(3) / 2.0 * r);
        double y = edgeLengthKm * (3.0 / 2.0 * r);
        return new double[]{x, y};
    }
}
