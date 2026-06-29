package staraxis.game.space.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * IrregularGalaxyGenerator（不规则星系生成器）。
 *
 * 恒星分布在多个随机团簇中，团簇位置和大小随机。
 * 没有明显的对称结构。
 */
public class IrregularGalaxyGenerator implements GalaxyGenerator {

    private static final long STAR_ID_START = 10_000_000L;

    @Override
    public GalaxyData generate(GalaxyConfig config) {
        SplittableRandom rng = new SplittableRandom(config.worldSeed);
        List<StarPosition> stars = new ArrayList<>(config.starCount);
        long nextId = STAR_ID_START;

        // 生成团簇中心
        double[][] clusterCenters = new double[config.clusterCount][3];
        double[] clusterRadii = new double[config.clusterCount];

        for (int c = 0; c < config.clusterCount; c++) {
            double r = rng.nextDouble() * config.galaxyRadius * 0.7;
            double theta = rng.nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);

            clusterCenters[c][0] = r * Math.sin(phi) * Math.cos(theta);
            clusterCenters[c][1] = r * Math.sin(phi) * Math.sin(theta) * 0.3;
            clusterCenters[c][2] = r * Math.cos(phi);

            clusterRadii[c] = config.galaxyRadius * (0.1 + rng.nextDouble() * 0.3);
        }

        // 分配恒星到团簇
        int starsPerCluster = config.starCount / config.clusterCount;
        int remaining = config.starCount;

        for (int c = 0; c < config.clusterCount; c++) {
            int count = (c == config.clusterCount - 1) ? remaining : starsPerCluster;
            remaining -= count;

            for (int i = 0; i < count; i++) {
                double r = Math.abs(rng.nextGaussian()) * clusterRadii[c] * 0.5;
                double theta = rng.nextDouble() * 2.0 * Math.PI;
                double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);

                double x = clusterCenters[c][0] + r * Math.sin(phi) * Math.cos(theta);
                double y = clusterCenters[c][1] + r * Math.sin(phi) * Math.sin(theta);
                double z = clusterCenters[c][2] + r * Math.cos(phi);

                SpectralType type = randomSpectralType(rng);
                double radius = type.minRadiusGU + rng.nextDouble() * (type.maxRadiusGU - type.minRadiusGU);
                long systemSeed = rng.nextLong();

                stars.add(new StarPosition(nextId++, x, y, z, type, radius, systemSeed));
            }
        }

        return new GalaxyData(config.worldSeed, GalaxyType.IRREGULAR, stars);
    }

    private SpectralType randomSpectralType(SplittableRandom rng) {
        double roll = rng.nextDouble();
        if (roll < 0.001) return SpectralType.O;
        if (roll < 0.01) return SpectralType.B;
        if (roll < 0.04) return SpectralType.A;
        if (roll < 0.10) return SpectralType.F;
        if (roll < 0.20) return SpectralType.G;
        if (roll < 0.40) return SpectralType.K;
        return SpectralType.M;
    }
}
