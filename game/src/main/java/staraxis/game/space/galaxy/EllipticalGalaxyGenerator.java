package staraxis.game.space.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * EllipticalGalaxyGenerator（椭圆星系生成器）。
 *
 * 恒星分布在椭球体内，中心密集，向外递减。
 * 使用高斯分布生成径向距离，椭球度控制 Y 轴压缩。
 */
public class EllipticalGalaxyGenerator implements GalaxyGenerator {

    private static final long STAR_ID_START = 10_000_000L;

    @Override
    public GalaxyData generate(GalaxyConfig config) {
        SplittableRandom rng = new SplittableRandom(config.worldSeed);
        List<StarPosition> stars = new ArrayList<>(config.starCount);
        long nextId = STAR_ID_START;

        double radiusXZ = config.galaxyRadius;
        double radiusY = radiusXZ * (1.0 - config.ellipticity);

        for (int i = 0; i < config.starCount; i++) {
            // 高斯分布：中心密集
            double r = Math.abs(rng.nextGaussian()) * radiusXZ * 0.4;
            r = Math.min(r, radiusXZ);

            double theta = rng.nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);

            double x = r * Math.sin(phi) * Math.cos(theta);
            double z = r * Math.sin(phi) * Math.sin(theta);
            double y = r * Math.cos(phi) * (radiusY / radiusXZ);

            SpectralType type = randomSpectralType(rng);
            double radius = type.minRadiusGU + rng.nextDouble() * (type.maxRadiusGU - type.minRadiusGU);
            long systemSeed = rng.nextLong();

            stars.add(new StarPosition(nextId++, x, y, z, type, radius, systemSeed));
        }

        return new GalaxyData(config.worldSeed, GalaxyType.ELLIPTICAL, stars);
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
