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

    /** 恒星描述文本。 */
    public String description;

    /**
     * 恒星纹理资源路径（相对于 assets/star/，例如 "star/star_yellow01.png"），无时为 null。
     */
    public String surfaceTexturePath;

    public StarBody() {
        this.entityType = EntityType.STAR;
    }
}
