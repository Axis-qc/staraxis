package staraxis.game.astro;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;

/**
 * PlanetBody（行星实体）
 *
 * 继承自 Entity，代表一个行星天体。
 */
public class PlanetBody extends Entity {

    /** 行星类型ID（planetTypeId），例如 "TERRESTRIAL"。 */
    public String planetTypeId;

    /** 行星半径（GU）。 */
    public double radiusGU;

    /**
     * 星球地表纹理资源路径（相对于 assets/planet/，例如
     * "planet/Solid/Terrestrial/Terrestrial_01-512x512.png"），无地表时为 null。
     */
    public String surfaceTexturePath;

    // --- Orbit params (merged) ---

    /** 轨道中心实体ID（orbitCenterEntityId）。 */
    public long orbitCenterEntityId;

    /** 轨道长半轴（GU）。 */
    public double semiMajorAxisGU;

    /** 轨道偏心率（0=圆，<1=椭圆）。 */
    public double eccentricity;

    /** 轨道倾角（度）。 */
    public double inclinationDeg;

    /** 近地点方向角（度）。 */
    public double periapsisArgDeg;

    /** 轨道周期（游戏日）。 */
    public double orbitalPeriodDays;

    /** 纪元时刻（t=0）的平近点角（度）。 */
    public double meanAnomalyDegAtEpoch;

    /** 自转周期（游戏小时）。 */
    public double rotationPeriodHours;

    public PlanetBody() {
        this.entityType = EntityType.PLANET;
    }
}
