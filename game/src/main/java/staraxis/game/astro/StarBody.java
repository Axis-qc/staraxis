package staraxis.game.astro;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;

/**
 * StarBody（恒星实体）
 *
 * 继承自 Entity，代表一个恒星天体。
 */
public class StarBody extends Entity {

    /** 恒星类型ID（starTypeId），例如 "G_MAIN_SEQUENCE"。 */
    public String starTypeId;

    /** 恒星半径（GU）。 */
    public double radiusGU;

    /** 恒星质量（太阳质量倍数）。 */
    public double massSolar;

    /** 表面温度（开尔文）。 */
    public int temperatureK;

    public StarBody() {
        this.entityType = EntityType.STAR;
    }
}
