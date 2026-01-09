package com.staraxis.universegen.util;

import com.staraxis.universegen.StarSystemGenerator;
import com.staraxis.universegen.model.Planet;
import com.staraxis.universegen.model.StarSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class NoOverlapTest {

    /**
     * 随机采样 1000 对行星，若发现 ≥1 起重叠则失败。
     */
    @Test
    void randomSample_noOverlap() {
        StarSystemGenerator gen = new StarSystemGenerator(1234L);
        StarSystem system = gen.generate("Test", 1.9885e30, 6, 8);

        // 将行星转换为 Body
        List<OverlapDetector.Body> bodies = new ArrayList<>();
        // StarSystem 本身不直接持有 planets；行星属于每颗 Star。
        for (var star : system.stars()) {
            for (Planet p : star.planets()) {
            bodies.add(new OverlapDetector.Body(
                    p.semiMajorAxisKm(), 0, 0, // 近似放在轨道圆上的 x 轴
                    p.radiusKm(),
                    p.name()));
            }
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (int i = 0; i < 1000; i++) {
            int a = rand.nextInt(bodies.size());
            int b;
            do { b = rand.nextInt(bodies.size()); } while (b == a);
            List<OverlapDetector.Body> pair = List.of(bodies.get(a), bodies.get(b));
            List<String> overlaps = OverlapDetector.findOverlaps(pair);
            if (!overlaps.isEmpty()) {
                fail("发现重叠: " + overlaps);
            }
        }
    }

    @Test
    void overlapDetected() {
        OverlapDetector.Body a = new OverlapDetector.Body(0,0,0, 100, "A");
        OverlapDetector.Body b = new OverlapDetector.Body(50,0,0, 60, "B"); // 50 < 0.999*(100+60)=159
        assertFalse(OverlapDetector.findOverlaps(List.of(a)).isEmpty() == false); // single body -> no overlap
        List<String> overlaps = OverlapDetector.findOverlaps(List.of(a,b));
        assertFalse(overlaps.isEmpty(), "应检测到重叠");
    }
}
