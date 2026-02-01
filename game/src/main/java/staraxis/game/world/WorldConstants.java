package staraxis.game.world;

/**
 * WorldConstants
 *
 * 坐标系与尺度的权威常量（game 层）。
 */
public final class WorldConstants {

    private WorldConstants() {
    }

    /**
     * 权威空间单位：GU。
     *
     * 在 1.0 缩放下：1 像素 = 1 GU。
     *
     * 物理换算：1 GU = 100,000 m。
     */
    public static final long GAME_UNIT_IN_METERS = 100_000L;

    /**
     * 1 AU in GU.
     */
    public static final double AU_IN_GU = 149_597_870_700.0 / (double) GAME_UNIT_IN_METERS;

    /**
     * 1 LY in GU.
     */
    public static final double LY_IN_GU = 94_607_304_725_808.0 / (double) GAME_UNIT_IN_METERS;

    /**
     * 仅用于外部数据/公式对接的米制常量（不允许把米当作 world 坐标单位使用）。
     */
    public static final double AU_IN_METERS = 149_597_870_700.0;

    /**
     * 仅用于外部数据/公式对接的米制常量（不允许把米当作 world 坐标单位使用）。
     */
    public static final double LY_IN_METERS = 94_607_304_725_808.0;

    /**
     * hex sector: flat-to-flat diameter in GU.
     */
    public static final double SECTOR_DIAMETER_GU = 200_000_000.0;

    /**
     * pointy-top hex: flat-to-flat diameter = 2 * size
     */
    public static final double SECTOR_SIZE_GU = SECTOR_DIAMETER_GU / 2.0;
}
