package com.staraxis.game.core.coordinate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 Spec SC-2：在巨大坐标范围内，近景移动的数值应保持连续（不出现明显跳变）。
 *
 * 这里用“差分计算连续性”做最小可执行验证：
 * - grid 坐标取一个很大的值
 * - offset 做小幅变化
 * - delta 结果应与变化量一致（误差极小）
 */
public class WorldCoordinateStabilityTest {

    @Test
    void deltaShouldBeContinuousForSmallOffsetChangesAtHugeGrid() {
        // 选择一个很大的 grid，模拟星系级位置
        int big = 1_000_000_000; // 10^9

        WorldCoordinate camera = new WorldCoordinate(big, big, big, 0.0, 0.0, 0.0);
        WorldCoordinate a = new WorldCoordinate(big, big, big, 10.0, 0.0, 0.0);
        WorldCoordinate b = new WorldCoordinate(big, big, big, 10.001, 0.0, 0.0);

        double da = a.deltaXKm(camera);
        double db = b.deltaXKm(camera);

        // 应精确反映 offset 的差异（double 下误差应极小）
        assertEquals(10.0, da, 1e-9);
        assertEquals(10.001, db, 1e-9);
        assertEquals(0.001, db - da, 1e-9);
    }
}
