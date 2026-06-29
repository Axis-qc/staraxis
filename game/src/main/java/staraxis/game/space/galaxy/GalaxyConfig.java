package staraxis.game.space.galaxy;

/**
 * GalaxyConfig（星系生成配置）。
 *
 * 控制星系生成的参数。不同 GalaxyType 使用不同的参数子集。
 */
public class GalaxyConfig {

    /** 星系类型。 */
    public GalaxyType galaxyType = GalaxyType.SPIRAL;

    /** 世界种子（确定性生成）。 */
    public long worldSeed = 12345L;

    /** 恒星总数。 */
    public int starCount = 5000;

    /** 星系半径（GU），默认 50000（填满半个 10万^3 盒子）。 */
    public double galaxyRadius = 50000.0;

    // ---- 螺旋臂星系专用参数 ----

    /** 螺旋臂数量（典型值 2-6）。 */
    public int spiralArms = 4;

    /** 螺旋臂倾角（弧度），~12度 = 0.209 rad 为典型值。 */
    public double pitchAngle = 0.209;

    /** 臂宽度系数（越大臂越宽，恒星分布越分散）。 */
    public double armWidth = 0.5;

    /** 中心隆起区域比例（0-1，bulge 区域占星系半径的比例）。 */
    public double bulgeRatio = 0.2;

    // ---- 椭圆星系专用参数 ----

    /** 椭圆度（0-1，0 = 正圆，1 = 极扁）。 */
    public double ellipticity = 0.3;

    // ---- 不规则星系专用参数 ----

    /** 团簇数量（不规则星系由多个随机团簇组成）。 */
    public int clusterCount = 8;

    /**
     * 创建默认螺旋臂星系配置。
     */
    public static GalaxyConfig defaultSpiral() {
        GalaxyConfig config = new GalaxyConfig();
        config.galaxyType = GalaxyType.SPIRAL;
        return config;
    }

    /**
     * 创建默认椭圆星系配置。
     */
    public static GalaxyConfig defaultElliptical() {
        GalaxyConfig config = new GalaxyConfig();
        config.galaxyType = GalaxyType.ELLIPTICAL;
        return config;
    }

    /**
     * 创建默认不规则星系配置。
     */
    public static GalaxyConfig defaultIrregular() {
        GalaxyConfig config = new GalaxyConfig();
        config.galaxyType = GalaxyType.IRREGULAR;
        return config;
    }
}
