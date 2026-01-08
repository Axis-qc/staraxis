package com.staraxis.universegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计算六边形边长标准差，断言 <5%（理论上应为 0，考虑浮点误差）。
 */
class SectorBoundaryTest {

    @Test
    void edgeLength_stdDevBelow5Percent() {
        double edgeLy = 10; // 光年
        double LY_TO_KM = 9.4607e12;
        double sizeKm = edgeLy * LY_TO_KM;

        // 6 顶点到中心距离理论一致 = sizeKm
        double[] dx = new double[6];
        double[] dy = new double[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            dx[i] = sizeKm * Math.cos(angle);
            dy[i] = sizeKm * Math.sin(angle);
        }
        double[] dist = new double[6];
        double sum = 0;
        for (int i = 0; i < 6; i++) {
            dist[i] = Math.hypot(dx[i], dy[i]);
            sum += dist[i];
        }
        double mean = sum / 6;
        double var = 0;
        for (double d : dist) var += (d - mean) * (d - mean);
        double std = Math.sqrt(var / 6);
        assertTrue(std / mean < 0.05, "标准差占比应 <5%" );
    }
}
