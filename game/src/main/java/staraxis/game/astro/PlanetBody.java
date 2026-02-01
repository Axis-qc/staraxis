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

    /** 轨道参数。 */
    public OrbitParams orbit;

    /** 自转周期（游戏小时）。 */
    public double rotationPeriodHours;

    public PlanetBody() {
        this.entityType = EntityType.PLANET;
    }
}
