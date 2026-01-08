package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Planet;
import com.staraxis.universegen.model.Sector;
import com.staraxis.universegen.model.Star;
import com.staraxis.universegen.model.StarSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * 星区生成器：为每个 sector 生成类型（galaxy/deep_space/nebula）以及可选的恒星系（star_system）。
 *
 * 当前实现目标：最小可用但“非占位”
 * - star_system：支持 1~3 恒星
 * - 每颗恒星可生成 0~N 行星（planetComplexity 暂不接入，这里用简单分布）
 */
public class SectorGenerator {

    public Sector generate(int id, UniverseGenConfig cfg, SplittableRandom rng) {
        String sectorType = sampleSectorType(cfg, rng);

        StarSystem starSystem = null;
        if ("galaxy".equals(sectorType)) {
            starSystem = generateStarSystem(id, rng);
        }

        return new Sector(id, sectorType, starSystem);
    }

    private String sampleSectorType(UniverseGenConfig cfg, SplittableRandom rng) {
        // 使用 cfg.starToDeepSpaceRatio 作为 galaxy vs deep_space 的比例。
        // nebula 先给一个很小的固定概率（后续可扩展为配置项）。
        double pNebula = 0.05;
        double pGalaxy = clamp01(cfg.getStarToDeepSpaceRatio());

        // 归一化：确保三者和为 1
        double remaining = Math.max(0.0, 1.0 - pNebula);
        if (pGalaxy > remaining) {
            pGalaxy = remaining;
        }
        double pDeep = remaining - pGalaxy;

        double roll = rng.nextDouble();
        if (roll < pGalaxy) {
            return "galaxy";
        }
        if (roll < pGalaxy + pNebula) {
            return "nebula";
        }
        return "deep_space";
    }

    private StarSystem generateStarSystem(int sectorId, SplittableRandom rng) {
        String systemName = "SYS-" + sectorId + "-" + Long.toUnsignedString(rng.nextLong(), 36);

        int starCount = sampleStarCount(rng);
        List<Star> stars = new ArrayList<>(starCount);
        for (int i = 0; i < starCount; i++) {
            stars.add(generateStar(systemName, i, rng));
        }

        return new StarSystem(systemName, stars);
    }

    private int sampleStarCount(SplittableRandom rng) {
        double roll = rng.nextDouble();
        if (roll < 0.70) {
            return 1;
        }
        if (roll < 0.90) {
            return 2;
        }
        return 3;
    }

    private Star generateStar(String systemName, int index, SplittableRandom rng) {
        String name = systemName + "-STAR-" + index;
        String type = sampleStarType(rng);
        double massKg = sampleStarMassKg(type, rng);

        int planetCount = samplePlanetCount(rng);
        List<Planet> planets = new ArrayList<>(planetCount);
        for (int i = 0; i < planetCount; i++) {
            planets.add(generatePlanet(name, i, rng));
        }

        return new Star(name, type, massKg, planets);
    }

    private String sampleStarType(SplittableRandom rng) {
        // 最小数据驱动前的简化：主序星类型
        double roll = rng.nextDouble();
        if (roll < 0.60) {
            return "yellow_dwarf";
        }
        if (roll < 0.85) {
            return "red_dwarf";
        }
        return "blue_giant";
    }

    private double sampleStarMassKg(String type, SplittableRandom rng) {
        // 极简：不同类型给不同范围（不是严格物理，但比占位强）
        return switch (type) {
            case "red_dwarf" -> lerp(0.08, 0.5, rng.nextDouble()) * 1.98847e30;
            case "yellow_dwarf" -> lerp(0.7, 1.3, rng.nextDouble()) * 1.98847e30;
            case "blue_giant" -> lerp(5.0, 20.0, rng.nextDouble()) * 1.98847e30;
            default -> 1.0 * 1.98847e30;
        };
    }

    private int samplePlanetCount(SplittableRandom rng) {
        // 最小可用：0~8
        // 让分布偏向少量行星
        double roll = rng.nextDouble();
        if (roll < 0.10) return 0;
        if (roll < 0.35) return 1;
        if (roll < 0.60) return 2;
        if (roll < 0.80) return 3;
        if (roll < 0.92) return 4;
        return 5 + rng.nextInt(4); // 5~8
    }

    private Planet generatePlanet(String starName, int orbitIndex, SplittableRandom rng) {
        String name = starName + "-P" + orbitIndex;

        // 半长轴 km：从 0.05AU 到 30AU（粗略）
        double auKm = 149_597_870.7;
        double semiMajorAxisKm = lerp(0.05, 30.0, rng.nextDouble()) * auKm;

        // 半径 km：从 2_000 到 70_000
        double radiusKm = lerp(2_000, 70_000, rng.nextDouble());

        // 轨道周期：简化估算（不严格），让它随半长轴增大而增大
        double orbitalPeriodSeconds = Math.max(60 * 60.0, Math.pow(semiMajorAxisKm / auKm, 1.5) * 365.25 * 24 * 3600);

        return new Planet(name, radiusKm, semiMajorAxisKm, orbitalPeriodSeconds);
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
