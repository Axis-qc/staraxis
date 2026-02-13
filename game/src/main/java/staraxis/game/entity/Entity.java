package staraxis.game.entity;

import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

/**
 * Entity
 *
 * 实体（Entity）：游戏世界中一切可独立索引、可归属、可移动的对象的统一基类。
 *
 * 统一字段口径：所有 ID 字段使用 xxxId 命名，便于记忆与查询。
 */
public class Entity {

    /** 全局唯一实体ID（主键）。 */
    public long entityId;

    /** 实体类型（用于逻辑分发与查询过滤）。 */
    public EntityType entityType;

    /** 所属恒星系ID（systemId），归属可变；无归属时为 0。 */
    public long systemId;

    /** 父实体ID（parentEntityId），用于表达层级归属；无父级时为 0。 */
    public long parentEntityId;

    /** 所在星区坐标（sectorCoord = sectorId 口径）。 */
    public SectorCoord sectorCoord;

    /** 世界坐标（GU）。 */
    public Vec2d posWorldGU;

    /** 世界速度（GU/tick 或 GU/hour，具体口径后续统一）。 */
    public Vec2d velWorldGU;

    /** 所属国家/文明 ID（nationId 口径，String 类型，与国家定义 ID 一致）。无归属时为 null。 */
    public String ownerNationId;

    public Entity() {
    }
}
