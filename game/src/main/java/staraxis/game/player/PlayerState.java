package staraxis.game.player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;

/**
 * PlayerState（玩家运行时状态）
 *
 * 玩家的一级状态对象。一个 PlayerState 对应一个真实/联机玩家。
 *
 * 核心职责：
 * 1. 维护 playerId 和显示名的映射
 * 2. 维护玩家所属国家ID（可选）
 * 3. 维护个人直接资产表（EntityType -> 实体ID集合）
 *
 * 与 NationState 平级，共同构成所有权管理层。
 * 个人资产表在玩家无国家时代表纯个人拥有，有国家时可选择捐献为国家资产。
 */
public class PlayerState {

    /** 玩家唯一标识（p_ 开头的 UUID）。 */
    public final String playerId;

    /** 玩家显示名（从 Account 同步）。 */
    public String displayName;

    /** 所属国家ID（可选，无国家时为 null）。 */
    public String nationId;

    /**
     * 个人直接资产表：EntityType -> 该类型下该玩家直接拥有的实体ID集合。
     *
     * 说明：
     * - 仅记录"直接所有权"的实体（如舰船、行星、空间站等），不展开层级关系。
     * - 所有权变更由 AssetManager 统一管理，业务层不直接操作。
     * - 玩家无国家时资产归个人，有国家时可捐献为国家公共资产。
     */
    public final Map<EntityType, Set<Long>> ownedEntityIdsByType = new HashMap<>();

    /**
     * 构造函数：创建指定ID的玩家运行时状态。
     *
     * @param playerId 玩家唯一标识
     */
    public PlayerState(String playerId) {
        this.playerId = playerId;
    }

    /**
     * 添加实体到个人资产表。
     *
     * @param entity 目标实体
     */
    public void addOwnedEntity(Entity entity) {
        if (entity == null || entity.entityType == null) {
            return;
        }
        addOwnedEntity(entity.entityType, entity.entityId);
    }

    /**
     * 添加实体ID到个人资产表。
     *
     * @param type     实体类型
     * @param entityId 实体ID
     */
    public void addOwnedEntity(EntityType type, long entityId) {
        if (type == null) {
            return;
        }
        ownedEntityIdsByType.computeIfAbsent(type, k -> new HashSet<>()).add(entityId);
    }

    /**
     * 从个人资产表移除实体。
     *
     * @param entity 目标实体
     */
    public void removeOwnedEntity(Entity entity) {
        if (entity == null || entity.entityType == null) {
            return;
        }
        removeOwnedEntity(entity.entityType, entity.entityId);
    }

    /**
     * 从个人资产表移除实体ID。
     *
     * @param type     实体类型
     * @param entityId 实体ID
     */
    public void removeOwnedEntity(EntityType type, long entityId) {
        if (type == null) {
            return;
        }
        Set<Long> ids = ownedEntityIdsByType.get(type);
        if (ids != null) {
            ids.remove(entityId);
            if (ids.isEmpty()) {
                ownedEntityIdsByType.remove(type);
            }
        }
    }

    /**
     * 获取个人在指定实体类型下直接拥有的实体ID集合的只读视图。
     *
     * @param type 实体类型
     * @return 实体ID集合，无则返回空集合
     */
    public Set<Long> getOwnedEntityIds(EntityType type) {
        Set<Long> ids = ownedEntityIdsByType.get(type);
        return ids != null ? java.util.Collections.unmodifiableSet(ids) : java.util.Collections.emptySet();
    }
}
