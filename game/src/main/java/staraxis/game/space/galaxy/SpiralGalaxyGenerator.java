package staraxis.game.space.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * SpiralGalaxyGenerator（螺旋臂星系生成器）。
 *
 * 使用对数螺旋公式生成恒星分布：
 *   theta(r) = theta0 + (1/tan(pitch)) * ln(r/r0)
 *
 * 恒星密度分布：
 * - 螺旋臂上密度高（高斯分布偏移）
 * - 臂间密度低
 * - 中心 bulge 区域密集（指数分布）
 *
 * 确定性：使用 worldSeed 派生的 SplittableRandom。
 */
public class SpiralGalaxyGenerator implements GalaxyGenerator {

    /** bulge 区域恒星占比。 */
    private static final double BULGE_FRACTION = 0.15;

    /** 臂上恒星占比（剩余部分分布在臂间）。 */
    private static final double ARM_FRACTION = 0.70;

    @Override
    public GalaxyData generate(GalaxyConfig config) {
        SplittableRandom rng = new SplittableRandom(config.worldSeed);

        int totalStars = config.starCount;
        int bulgeCount = (int) (totalStars * BULGE_FRACTION);
        int armCount = (int) (totalStars * ARM_FRACTION);
        int interArmCount = totalStars - bulgeCount - armCount;

        List<StarPosition> stars = new ArrayList<>(totalStars);
        long nextId = STAR_ID_START;

        // 1. 生成 bulge 区域恒星（中心密集）
        for (int i = 0; i < bulgeCount; i++) {
            stars.add(generateBulgeStar(nextId++, config, rng));
        }

        // 2. 生成螺旋臂上恒星
        for (int i = 0; i < armCount; i++) {
            int armIndex = i % config.spiralArms;
            stars.add(generateArmStar(nextId++, config, armIndex, rng));
        }

        // 3. 生成臂间散布恒星
        for (int i = 0; i < interArmCount; i++) {
            stars.add(generateInterArmStar(nextId++, config, rng));
        }

        return new GalaxyData(config.worldSeed, GalaxyType.SPIRAL, stars);
    }

    /**
     * 生成 bulge 区域恒星。
     * 使用指数分布集中在中心。
     */
    private StarPosition generateBulgeStar(long id, GalaxyConfig config, SplittableRandom rng) {
        double bulgeRadius = config.galaxyRadius * config.bulgeRatio;

        // 指数分布：越靠近中心越密集
        double r = bulgeRadius * Math.abs(rng.nextGaussian()) * 0.5;
        r = Math.min(r, bulgeRadius);

        double theta = rng.nextDouble() * 2.0 * Math.PI;
        double x = r * Math.cos(theta);
        double z = r * Math.sin(theta);

        // bulge 区域 Y 方向有较高分散
        double y = rng.nextGaussian() * bulgeRadius * 0.15;

        return createStar(id, x, y, z, rng);
    }

    /**
     * 生成螺旋臂上恒星。
     * 沿对数螺旋分布，有宽度偏移。
     */
    private StarPosition generateArmStar(long id, GalaxyConfig config, int armIndex, SplittableRandom rng) {
        double r0 = config.galaxyRadius * config.bulgeRatio;
        double maxR = config.galaxyRadius;

        // 半径：从 bulge 边缘到星系边缘，均匀分布
        double r = r0 + rng.nextDouble() * (maxR - r0);

        // 对数螺旋角度
        double armBaseAngle = armIndex * (2.0 * Math.PI / config.spiralArms);
        double spiralAngle = armBaseAngle + (1.0 / Math.tan(config.pitchAngle)) * Math.log(r / r0);

        // 臂宽度偏移（高斯分布）
        double armWidthGU = config.armWidth * r * 0.1;
        double offset = rng.nextGaussian() * armWidthGU;
        double perpendicularAngle = spiralAngle + Math.PI / 2.0;

        double x = r * Math.cos(spiralAngle) + offset * Math.cos(perpendicularAngle);
        double z = r * Math.sin(spiralAngle) + offset * Math.sin(perpendicularAngle);

        // Y 方向：盘面薄，远处稍厚
        double diskHeight = config.galaxyRadius * 0.02 * (r / maxR);
        double y = rng.nextGaussian() * diskHeight;

        return createStar(id, x, y, z, rng);
    }

    /**
     * 生成臂间散布恒星。
     * 随机分布在盘面上，密度较低。
     */
    private StarPosition generateInterArmStar(long id, GalaxyConfig config, SplittableRandom rng) {
        double r0 = config.galaxyRadius * config.bulgeRatio;

        // 半径分布
        double r = r0 + Math.pow(rng.nextDouble(), 0.7) * (config.galaxyRadius - r0);

        double theta = rng.nextDouble() * 2.0 * Math.PI;
        double x = r * Math.cos(theta);
        double z = r * Math.sin(theta);

        // Y 方向：薄盘
        double diskHeight = config.galaxyRadius * 0.03;
        double y = rng.nextGaussian() * diskHeight;

        return createStar(id, x, y, z, rng);
    }

}
