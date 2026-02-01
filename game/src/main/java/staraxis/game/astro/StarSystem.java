package staraxis.game.astro;

import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * StarSystem（恒星系）
 *
 * 恒星系：一个逻辑组织，用于表达“这几颗恒星与行星属于同一系统/同一重心参考系”。
 */
public class StarSystem {
    /** 恒星系ID（systemId）。 */
    public long systemId;

    /** 恒星系重心实体ID（barycenterEntityId），用于行星轨道指向。 */
    public long barycenterEntityId;

    /** 恒星系当前所在星区坐标（sectorCoord = sectorId 口径）。 */
    public SectorCoord sectorCoord;

    /** 恒星系重心世界坐标（centerWorldGU），可随恒星运动更新。 */
    public Vec2d centerWorldGU;

    /** 属于该系统的恒星实体列表。 */
    public final List<StarBody> stars = new ArrayList<>();

    /** 属于该系统的行星实体列表。 */
    public final List<PlanetBody> planets = new ArrayList<>();
}
