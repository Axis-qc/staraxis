package com.staraxis.universegen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SectorLocatorServiceTest {

    @Test
    void generateSectorIdsByRadius_countsMatchFormula() {
        SectorLocatorService locator = new SectorLocatorService(1.0);

        assertEquals(1, locator.generateSectorIdsByRadius(0).size());
        assertEquals(7, locator.generateSectorIdsByRadius(1).size());

        int r = 8;
        int expected = 1 + 3 * r * (r + 1);
        assertEquals(expected, locator.generateSectorIdsByRadius(r).size());
    }

    @Test
    void locateCenter_originSector() {
        double edgeLy = 10; // arbitrary
        SectorLocatorService locator = new SectorLocatorService(edgeLy);
        CoordinateSystem coord = locator.locateCenter(0L);
        assertEquals(0, coord.getXKm(), 1e-6, "Origin sector X should be 0");
        assertEquals(0, coord.getYKm(), 1e-6, "Origin sector Y should be 0");
        assertEquals(0, coord.getZKm(), 1e-6);
    }

    @Test
    void locateCenter_positiveAxial() {
        double edgeLy = 5;
        SectorLocatorService locator = new SectorLocatorService(edgeLy);
        long q = 1;
        long r = 2;
        long id = (q << 32) | r;
        CoordinateSystem coord = locator.locateCenter(id);

        double lyToKm = 9_460_730_472_580.8;
        double edgeLengthKm = edgeLy * lyToKm;
        double expectedX = edgeLengthKm * (Math.sqrt(3) * q + Math.sqrt(3) / 2.0 * r);
        double expectedY = edgeLengthKm * (3.0 / 2.0 * r);

        // coord.getXKm()/getYKm 是 double 计算结果；这里用相对误差 1e-6 覆盖浮点误差。
        assertEquals(expectedX, coord.getXKm(), Math.abs(expectedX) * 1e-6);
        assertEquals(expectedY, coord.getYKm(), Math.abs(expectedY) * 1e-6);
    }

    @Test
    void neighborCenterDistance_matchesOneLyRule_withinOnePercent() {
        // 依据当前实现：sectorEdgeLy 表示六边形边长（edge length）=1ly。
        // pointy-top axial 坐标中相邻中心距 = sqrt(3) * edgeLength。
        double edgeLy = 1.0;
        SectorLocatorService locator = new SectorLocatorService(edgeLy);

        long a = SectorLocatorService.packAxialToSectorId(0, 0);
        long b = SectorLocatorService.packAxialToSectorId(1, 0);

        CoordinateSystem ca = locator.locateCenter(a);
        CoordinateSystem cb = locator.locateCenter(b);

        double dx = cb.getXKm() - ca.getXKm();
        double dy = cb.getYKm() - ca.getYKm();
        double actualKm = Math.hypot(dx, dy);

        double lyToKm = 9_460_730_472_580.8;
        double expectedKm = Math.sqrt(3) * lyToKm; // edgeLy=1

        double relErr = Math.abs(actualKm - expectedKm) / expectedKm;
        assertTrue(relErr <= 0.01, "相邻中心距误差应 <=1%，actual=" + actualKm + " expected=" + expectedKm + " relErr=" + relErr);
    }
}
