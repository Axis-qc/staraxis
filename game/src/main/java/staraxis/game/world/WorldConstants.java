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
     * 1 GU = 100,000 meters.
     */
    public static final long GAME_UNIT_IN_METERS = 100_000L;

    /**
     * 1 AU in meters.
     */
    public static final double AU_IN_METERS = 149_597_870_700.0;

    /**
     * 1 LY in meters.
     */
    public static final double LY_IN_METERS = 94_607_304_725_808.0;

    public static final double AU_IN_GU = AU_IN_METERS / (double) GAME_UNIT_IN_METERS;

    /**
     * hex sector: flat-to-flat diameter in GU.
     */
    public static final double SECTOR_DIAMETER_GU = 200_000_000.0;

    /**
     * pointy-top hex: flat-to-flat diameter = 2 * size
     */
    public static final double SECTOR_SIZE_GU = SECTOR_DIAMETER_GU / 2.0;
}
