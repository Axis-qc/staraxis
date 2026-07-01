package staraxis.game.space.system;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import staraxis.game.space.OrbitalElements;
import staraxis.game.space.galaxy.StarPosition;

/**
 * StarSystemGenerator（恒星系生成器）。
 *
 * 根据恒星位置数据延迟生成恒星系内容（行星数量、轨道、类型）。
 * 确定性：相同 systemSeed -> 完全相同的恒星系。
 *
 * 行星分布规则（真实 GU 尺度，生成后累进缩放）：
 * - 热区（近恒星，< 500000 GU）：岩石/熔岩行星为主
 * - 宜居带（500000-2000000 GU）：岩石/海洋行星
 * - 冷区（2000000-8000000 GU）：气态巨星/冰封行星
 * - 远端（> 8000000 GU）：冰封行星/小行星
 */
public class StarSystemGenerator {

    /** 行星ID起始值。 */
    private static final long PLANET_ID_START = 20_000_000L;

    /** 热区边界（GU）。 */
    private static final double HOT_ZONE = 500000.0;

    /** 宜居带外边界（GU）。 */
    private static final double HABITABLE_ZONE = 2000000.0;

    /** 冷区外边界（GU）。 */
    private static final double COLD_ZONE = 8000000.0;

    /** 最大轨道半径（GU）。 */
    private static final double MAX_ORBIT = 20000000.0;

    /**
     * 根据恒星数据生成恒星系。
     *
     * @param star 恒星位置数据（包含 systemSeed）
     * @param worldSeed 世界种子（用于额外随机性）
     * @return 恒星系数据
     */
    public StarSystemData generate(StarPosition star, long worldSeed) {
        // 组合种子：systemSeed + worldSeed
        long combinedSeed = star.systemSeed() ^ (worldSeed << 16);
        SplittableRandom rng = new SplittableRandom(combinedSeed);

        // 行星数量：2-8 颗，按正态分布
        int planetCount = 2 + (int) Math.abs(rng.nextGaussian() * 2.0);
        planetCount = Math.min(planetCount, 8);

        List<PlanetData> planets = new ArrayList<>(planetCount);
        long nextPlanetId = PLANET_ID_START + (star.starId() % 1_000_000) * 10;

        // 生成行星轨道（从内到外递增，真实 GU 尺度）
        double currentOrbit = 500000.0 + rng.nextDouble() * 500000.0;

        for (int i = 0; i < planetCount; i++) {
            // 轨道间距：递增
            double orbitSpacing = 1.4 + rng.nextDouble() * 0.6;
            currentOrbit *= orbitSpacing;
            if (currentOrbit > MAX_ORBIT) break;

            // 根据轨道位置决定行星类型
            PlanetType type = selectPlanetType(currentOrbit, rng);

            // 累进缩放轨道
            double orbitScale = 40 + i * 10;
            double scaledOrbit = currentOrbit / orbitScale;

            // 轨道根数（使用缩放后的轨道值）
            OrbitalElements orbit = generateOrbitalElements(scaledOrbit, i, rng);

            // 行星半径（类型范围内随机）
            double radius = type.minRadiusGU + rng.nextDouble() * (type.maxRadiusGU - type.minRadiusGU);

            // 颜色（类型基础色 + 随机偏移）
            float colorR = clamp(type.colorR + (rng.nextFloat() - 0.5f) * 0.1f, 0, 1);
            float colorG = clamp(type.colorG + (rng.nextFloat() - 0.5f) * 0.1f, 0, 1);
            float colorB = clamp(type.colorB + (rng.nextFloat() - 0.5f) * 0.1f, 0, 1);

            planets.add(new PlanetData(nextPlanetId++, type, orbit, radius, colorR, colorG, colorB));
        }

        return new StarSystemData(star.starId(), star, planets);
    }

    /**
     * 根据轨道位置选择行星类型。
     */
    private PlanetType selectPlanetType(double orbitRadius, SplittableRandom rng) {
        double roll = rng.nextDouble();

        if (orbitRadius < HOT_ZONE) {
            // 热区：岩石/熔岩为主
            if (roll < 0.3) return PlanetType.LAVA;
            if (roll < 0.9) return PlanetType.ROCKY;
            return PlanetType.ICE; // 罕见
        } else if (orbitRadius < HABITABLE_ZONE) {
            // 宜居带：岩石/海洋
            if (roll < 0.4) return PlanetType.ROCKY;
            if (roll < 0.8) return PlanetType.OCEAN;
            return PlanetType.GAS_GIANT; // 小型气态
        } else if (orbitRadius < COLD_ZONE) {
            // 冷区：气态巨星/冰封
            if (roll < 0.5) return PlanetType.GAS_GIANT;
            if (roll < 0.8) return PlanetType.ICE;
            return PlanetType.ROCKY;
        } else {
            // 远端：冰封为主
            if (roll < 0.7) return PlanetType.ICE;
            return PlanetType.ROCKY;
        }
    }

    /**
     * 生成轨道根数。
     *
     * @param semiMajorAxis 半长轴（GU）
     * @param planetIndex 行星序号（用于周期计算）
     * @param rng 随机数生成器
     * @return 轨道根数
     */
    private OrbitalElements generateOrbitalElements(double semiMajorAxis, int planetIndex, SplittableRandom rng) {
        // 偏心率：大多数接近圆轨道
        double eccentricity = Math.abs(rng.nextGaussian()) * 0.1;
        eccentricity = Math.min(eccentricity, 0.5);

        // 倾角：小角度随机（弧度）
        double inclination = Math.abs(rng.nextGaussian()) * 0.05;
        inclination = Math.min(inclination, 0.3);

        // 升交点经度：随机
        double longitudeOfAscendingNode = rng.nextDouble() * 2.0 * Math.PI;

        // 近心点幅角：随机
        double argumentOfPeriapsis = rng.nextDouble() * 2.0 * Math.PI;

        // 历元平近点角：随机初始位置
        double meanAnomalyAtEpoch = rng.nextDouble() * 2.0 * Math.PI;

        // 历元时间：0
        double epoch = 0.0;

        // 轨道周期：根据轨道位置设计（不是开普勒第三定律）
        // 内行星 ~300s，宜居带 ~600s，外行星 ~3000s，远端 ~12000s
        double period = computeOrbitalPeriod(semiMajorAxis);

        return new OrbitalElements(
            semiMajorAxis,
            eccentricity,
            inclination,
            longitudeOfAscendingNode,
            argumentOfPeriapsis,
            meanAnomalyAtEpoch,
            epoch,
            period
        );
    }

    /**
     * 根据轨道位置计算轨道周期（游戏秒）。
     * 设计值，非物理公式。
     */
    private double computeOrbitalPeriod(double semiMajorAxis) {
        // 简单模型：周期与半长轴的 1.5 次方成正比
        // 参考：10000 GU -> ~600s（10分钟）
        double basePeriod = 600.0;
        double baseAxis = 10000.0;
        return basePeriod * Math.pow(semiMajorAxis / baseAxis, 1.5);
    }

    private static float clamp(double value, double min, double max) {
        return (float) Math.max(min, Math.min(max, value));
    }
}
