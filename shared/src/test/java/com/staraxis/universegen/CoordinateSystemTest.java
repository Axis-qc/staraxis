package com.staraxis.universegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 坐标系统测试：验证六边形边长=1ly 时，相邻星区中心距满足理论值（允许 1% 误差）。
 */
class CoordinateSystemTest {

    @Test
    void neighborCenterDistance_matchesTheoryWithin1Percent() {
        double edgeLy = 1.0;
        SectorLocatorService locator = new SectorLocatorService(edgeLy);

        // 相邻 axial 坐标：(0,0) 与 (1,0)
        long aId = SectorLocatorService.packAxialToSectorId(0, 0);
        long bId = SectorLocatorService.packAxialToSectorId(1, 0);

        CoordinateSystem a = locator.locateCenter(aId);
        CoordinateSystem b = locator.locateCenter(bId);

        double dx = b.getXKm() - a.getXKm();
        double dy = b.getYKm() - a.getYKm();
        double distKm = Math.hypot(dx, dy);

        // pointy-top axial：相邻中心距 = sqrt(3) * edgeLength
        double lyToKm = 9_460_730_472_580.8;
        double expectedKm = Math.sqrt(3) * edgeLy * lyToKm;

        double relErr = Math.abs(distKm - expectedKm) / expectedKm;
        assertTrue(relErr <= 0.01, "相邻中心距相对误差应 <= 1%，实际=" + relErr);
    }
}
