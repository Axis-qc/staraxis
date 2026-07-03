package staraxis.game.space.galaxy;

import java.util.SplittableRandom;

/**
 * GalaxyGenerator（星系生成器接口）。
 *
 * 根据配置生成星系中所有恒星的位置数据。
 * 不同 GalaxyType 对应不同实现。
 *
 * 确定性保证：相同 seed + 相同配置 -> 完全相同的星系。
 */
public interface GalaxyGenerator {

    /** 恒星ID起始值（避免与预留ID冲突）。 */
    long STAR_ID_START = 10_000_000L;

    /**
     * 生成星系数据。
     *
     * @param config 星系生成配置
     * @return 包含所有恒星位置数据的 GalaxyData
     */
    GalaxyData generate(GalaxyConfig config);

    /**
     * 根据位置创建恒星数据。
     * 随机分配光谱类型和半径。
     */
    default StarPosition createStar(long id, double x, double y, double z, SplittableRandom rng) {
        SpectralType type = randomSpectralType(rng);
        double radius = type.minRadiusGU + rng.nextDouble() * (type.maxRadiusGU - type.minRadiusGU);
        long systemSeed = rng.nextLong();
        return new StarPosition(id, x, y, z, type, radius, systemSeed);
    }

    /**
     * 随机光谱类型（按真实比例加权）。
     * M 型最常见（~73%），O 型最稀少（~0.1%）。
     */
    default SpectralType randomSpectralType(SplittableRandom rng) {
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
