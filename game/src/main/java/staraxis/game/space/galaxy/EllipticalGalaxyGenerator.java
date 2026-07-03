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

            stars.add(createStar(nextId++, x, y, z, rng));
        }

        return new GalaxyData(config.worldSeed, GalaxyType.ELLIPTICAL, stars);
    }

}
