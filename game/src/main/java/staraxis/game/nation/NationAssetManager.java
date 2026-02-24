package staraxis.game.nation;

import java.util.Collection;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.state.WorldState;

/**
 * NationAssetManager（国家资产管理器）
 *
 * 作用：集中管理“国家 <-> 实体”之间的资产归属关系，保证以下状态的一致性：
 * 1. Entity.ownerNationId（实体所属国家/文明ID）
 * 2. NationState.ownedEntityIdsByType（按实体类型聚合的国家直接资产表）
 *
 * 使用约定：
 * - 所有涉及实体所有权变更的逻辑（开局分配、中途加入、占领/割让等）必须通过本管理器完成。
 * - 外部代码禁止直接修改 Entity.ownerNationId 或 NationState.ownedEntityIdsByType，以防破坏一致性。
 */
public class NationAssetManager {

    /** 权威世界状态引用，用于访问实体与国家状态。 */
    private final WorldState worldState;

    /**
     * 构造函数。
     *
     * @param worldState 世界权威状态（WorldState）
     */
    public NationAssetManager(WorldState worldState) {
        this.worldState = worldState;
    }

    /**
     * 将指定实体的所有权分配给目标国家。
     *
     * 说明：
     * - 如果实体当前已有所有权，则会先从旧国家的资产表中移除。
     * - 然后更新 Entity.ownerNationId，并添加到新国家的资产表中。
     * - 如果传入的 nationId 为 null，则等价于释放所有权（调用 releaseEntityOwnership）。
     *
     * @param entityId 实体ID
     * @param nationId 目标国家ID（为 null 时表示释放为公共/无主）
     */
    public void assignEntityToNation(long entityId, String nationId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) {
            return;
        }
        if (nationId == null) {
            // 释放所有权，走统一逻辑
            releaseEntityOwnership(entityId);
            return;
        }

        // 1. 如果之前有归属国家，则先从旧国家资产表移除
        NationState oldNation = null;
        if (entity.ownerNationId != null) {
            oldNation = worldState.nationManager.getNationState(entity.ownerNationId);
            if (oldNation != null) {
                oldNation.removeOwnedEntity(entity);
            }
        }

        // 2. 更新实体所属国家ID
        entity.ownerNationId = nationId;

        // 3. 将实体添加到新国家资产表
        NationState newNation = worldState.nationManager.getNationState(nationId);
        if (newNation != null) {
            newNation.addOwnedEntity(entity);
        }

        // 标记低频快照为脏，便于尽快通过低频基线快照同步资产变更喵
        worldState.baselineDirty = true;

        // 标记情报系统为脏，触发探测等级重算喵
        if (worldState.intelSystem != null) {
            worldState.intelSystem.markDirty(nationId);
            if (oldNation != null) {
                worldState.intelSystem.markDirty(oldNation.nationId);
            }
        }
    }

    /**
     * 批量将实体的所有权分配给目标国家。
     *
     * @param entityIds 实体ID集合
     * @param nationId  目标国家ID（为 null 时表示释放为公共/无主）
     */
    public void assignEntitiesToNation(Collection<Long> entityIds, String nationId) {
        if (entityIds == null) {
            return;
        }
        for (Long id : entityIds) {
            if (id != null) {
                assignEntityToNation(id, nationId);
            }
        }
    }

    /**
     * 释放指定实体的所有权，使其归为公共/无主（ownerNationId = null）。
     *
     * 说明：
     * - 如果实体当前属于某国家，会从该国家的资产表中移除。
     * - 然后将 Entity.ownerNationId 置为 null。
     *
     * @param entityId 实体ID
     */
    public void releaseEntityOwnership(long entityId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) {
            return;
        }

        String oldNationId = entity.ownerNationId;
        if (oldNationId != null) {
            NationState oldNation = worldState.nationManager.getNationState(oldNationId);
            if (oldNation != null) {
                oldNation.removeOwnedEntity(entity);
            }
        }

        entity.ownerNationId = null;

        // 标记低频快照为脏喵
        worldState.baselineDirty = true;

        // 标记情报系统为脏喵
        if (worldState.intelSystem != null && oldNationId != null) {
            worldState.intelSystem.markDirty(oldNationId);
        }
    }

    /**
     * 在两国之间转移一组实体的所有权。
     *
     * 说明：
     * - fromNationId 为提示性参数，用于未来做一致性校验（当前不强制要求匹配）。
     * - 实际转移逻辑依然委托给 assignEntityToNation。
     *
     * @param entityIds    实体ID集合
     * @param fromNationId 源国家ID（可为 null）
     * @param toNationId   目标国家ID（不可为 null）
     */
    public void transferEntitiesBetweenNations(Collection<Long> entityIds, String fromNationId, String toNationId) {
        if (entityIds == null || toNationId == null) {
            return;
        }
        for (Long id : entityIds) {
            if (id == null) {
                continue;
            }
            Entity entity = worldState.entitiesById.get(id);
            if (entity == null) {
                continue;
            }
            // 可在未来加入断言：如果 fromNationId 不为 null，则要求 entity.ownerNationId 等于 fromNationId
            assignEntityToNation(id, toNationId);
        }
    }

    /**
     * 查询指定国家在某个实体类型下直接拥有的实体ID集合。
     *
     * @param nationId 国家ID
     * @param type     实体类型
     * @return 该国家在该类型下的实体ID集合（只读视图，若无则为空集合）
     */
    public java.util.Set<Long> getOwnedEntityIds(String nationId, EntityType type) {
        if (nationId == null || type == null) {
            return java.util.Collections.emptySet();
        }
        NationState state = worldState.nationManager.getNationState(nationId);
        if (state == null) {
            return java.util.Collections.emptySet();
        }
        return state.getOwnedEntityIds(type);
    }
}
