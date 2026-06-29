package staraxis.game.space.galaxy;

/**
 * GalaxyGeneratorFactory（星系生成器工厂）。
 *
 * 根据 GalaxyType 创建对应的 GalaxyGenerator 实现。
 */
public final class GalaxyGeneratorFactory {

    private GalaxyGeneratorFactory() {
    }

    /**
     * 根据星系类型创建对应的生成器。
     *
     * @param type 星系类型
     * @return 对应的 GalaxyGenerator 实现
     */
    public static GalaxyGenerator create(GalaxyType type) {
        return switch (type) {
            case SPIRAL -> new SpiralGalaxyGenerator();
            case ELLIPTICAL -> new EllipticalGalaxyGenerator();
            case IRREGULAR -> new IrregularGalaxyGenerator();
        };
    }
}
