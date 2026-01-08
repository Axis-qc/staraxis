package com.staraxis.universegen.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OverlapDetector 用于验证天体之间是否发生重叠（中心距离 &lt; 半径和的 99.9%）。
 *
 * 仅作为生成后质量保证工具，不参与实时逻辑。
 */
public final class OverlapDetector {

    public record Body(double xKm, double yKm, double zKm, double radiusKm, String name) {}

    private static final double THRESHOLD = 0.999; // 99.9% 允许极小浮点误差

    private OverlapDetector() {}

    /**
     * 返回所有重叠体对描述，若列表为空意味着通过检测。
     */
    public static List<String> findOverlaps(List<Body> bodies) {
        if (bodies == null || bodies.size() < 2) return Collections.emptyList();
        List<String> overlaps = new ArrayList<>();
        int n = bodies.size();
        for (int i = 0; i < n - 1; i++) {
            Body a = bodies.get(i);
            for (int j = i + 1; j < n; j++) {
                Body b = bodies.get(j);
                double distSq = distanceSq(a, b);
                double minDist = (a.radiusKm + b.radiusKm) * THRESHOLD;
                if (distSq < minDist * minDist) {
                    overlaps.add(a.name + " <-> " + b.name);
                }
            }
        }
        return overlaps;
    }

    private static double distanceSq(Body a, Body b) {
        double dx = a.xKm - b.xKm;
        double dy = a.yKm - b.yKm;
        double dz = a.zKm - b.zKm;
        return dx * dx + dy * dy + dz * dz;
    }
}
