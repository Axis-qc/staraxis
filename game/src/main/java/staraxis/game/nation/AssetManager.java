package staraxis.game.nation;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.player.PlayerState;
import staraxis.game.state.WorldState;

/**
 * AssetManager（资产管理器）
 *
 * 统一管理 Player 和 Nation 两个维度的实体所有权：
 * - Entity.ownerPlayerId ← PlayerState.ownedEntityIdsByType
 * - Entity.ownerNationId ← NationState.ownedEntityIdsByType
 *
 * 所有所有权变更必须通过本管理器完成，禁止外部直接修改归属字段。
 *
 * 核心原则：
 * - 所有权状态在 game 模块直接修改 Entity 字段，快照系统自动捕获变更后下发
 * - 本类负责维护 Entity 与 PlayerState/NationState 资产表的一致性
 * - 开局不分资产，所有权变更由具体操作（殖民、建造、捐献等）触发
 */
public class AssetManager {

    /** 权威世界状态引用。 */
    private final WorldState worldState;

    /**
     * 构造函数。
     *
     * @param worldState 世界权威状态（WorldState）
     */
    public AssetManager(WorldState worldState) {
        this.worldState = worldState;
    }

    // ============================
    //  玩家所有权操作
    // ============================

    /**
     * 分配实体给玩家。
     * 设置 Entity.ownerPlayerId，加入 PlayerState 资产表。
     *
     * @param entityId 实体ID
     * @param playerId 目标玩家ID
     */
    public void assignToPlayer(long entityId, String playerId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) return;
        PlayerState player = worldState.playerManager.getPlayerState(playerId);
        if (player == null) return;

        // 从旧玩家资产表移除
        if (entity.ownerPlayerId != null) {
            PlayerState oldOwner = worldState.playerManager.getPlayerState(entity.ownerPlayerId);
            if (oldOwner != null) oldOwner.removeOwnedEntity(entity);
        }

        entity.ownerPlayerId = playerId;
        player.addOwnedEntity(entity);
        worldState.markRealtimeDirty();
    }

    /**
     * 释放实体的玩家所有权。
     *
     * @param entityId 实体ID
     */
    public void releasePlayerOwnership(long entityId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) return;

        if (entity.ownerPlayerId != null) {
            PlayerState oldOwner = worldState.playerManager.getPlayerState(entity.ownerPlayerId);
            if (oldOwner != null) oldOwner.removeOwnedEntity(entity);
        }
        entity.ownerPlayerId = null;
        worldState.markRealtimeDirty();
    }

    // ============================
    //  国家所有权操作
    // ============================

    /**
     * 分配实体给国家。
     * 设置 Entity.ownerNationId，加入 NationState 资产表。
     *
     * @param entityId 实体ID
     * @param nationId 目标国家ID
     */
    public void assignToNation(long entityId, String nationId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) return;
        NationState nation = worldState.nationManager.getNationState(nationId);
        if (nation == null) return;

        // 从旧国家资产表移除
        if (entity.ownerNationId != null) {
            NationState oldNation = worldState.nationManager.getNationState(entity.ownerNationId);
            if (oldNation != null) oldNation.removeOwnedEntity(entity);
        }

        entity.ownerNationId = nationId;
        nation.addOwnedEntity(entity);
        worldState.baselineDirty = true;
        worldState.markRealtimeDirty();
    }

    /**
     * 释放实体的国家所有权。
     *
     * @param entityId 实体ID
     */
    public void releaseNationOwnership(long entityId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) return;

        if (entity.ownerNationId != null) {
            NationState oldNation = worldState.nationManager.getNationState(entity.ownerNationId);
            if (oldNation != null) oldNation.removeOwnedEntity(entity);
        }
        entity.ownerNationId = null;
        worldState.baselineDirty = true;
        worldState.markRealtimeDirty();
    }

    /**
     * 完全释放实体所有权（两个 owner 均置 null）。
     *
     * @param entityId 实体ID
     */
    public void releaseAllOwnership(long entityId) {
        releasePlayerOwnership(entityId);
        releaseNationOwnership(entityId);
    }

    /**
     * 声明恒星系归属给国家（星区级统一归属 API）喵。
     *
     * 作用：
     * - 同步设置 StarSystem.ownerNationId（星区归属）喵。
     * - 将系统内恒星实体登记进 NationState 资产表，保持归属与资产一致性喵。
     * - 不自动分配系统内行星（行星归属由殖民等具体操作决定，保持可殖民态）喵。
     *
     * 使用场景：
     * - 开局母星系声明（NationSpawnService）喵。
     * - 殖民成功后行星所在星区声明归属（ColonizePlanetHandler）喵。
     *
     * @param system   目标恒星系
     * @param nationId 目标国家ID
     */
    public void assignStarSystemClaimToNation(StarSystem system, String nationId) {
        if (system == null || nationId == null || nationId.isBlank()) {
            return;
        }
        // 已被其他国家占据时不越权覆盖喵
        if (system.ownerNationId != null && !system.ownerNationId.isBlank()
                && !system.ownerNationId.equals(nationId)) {
            return;
        }
        system.ownerNationId = nationId;
        // 同步系统内恒星归属（统一走 assignToNation，保证资产表一致）喵
        if (system.stars != null) {
            for (StarBody star : system.stars) {
                if (star != null) {
                    assignToNation(star.entityId, nationId);
                }
            }
        }
    }

    // ============================
    //  跨层级转移
    // ============================

    /**
     * 玩家捐献实体给国家。
     * ownerPlayerId 保留，ownerNationId 设为 nationId。
     * 实体同时出现在 PlayerState 和 NationState 资产表中。
     *
     * @param entityId 实体ID
     * @param playerId 玩家ID（须与当前 ownerPlayerId 一致）
     * @param nationId 目标国家ID
     */
    public void donateToNation(long entityId, String playerId, String nationId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) return;
        if (entity.ownerPlayerId == null || !entity.ownerPlayerId.equals(playerId)) return;

        NationState nation = worldState.nationManager.getNationState(nationId);
        if (nation == null) return;

        entity.ownerNationId = nationId;
        nation.addOwnedEntity(entity);
        worldState.baselineDirty = true;
        worldState.markRealtimeDirty();
    }

    /**
     * 国家授予实体给玩家。
     * ownerNationId 保留，ownerPlayerId 设为 playerId。
     * 实体同时出现在 NationState 和 PlayerState 资产表中。
     *
     * @param entityId 实体ID
     * @param nationId 国家ID（须与当前 ownerNationId 一致）
     * @param playerId 目标玩家ID
     */
    public void grantToPlayer(long entityId, String nationId, String playerId) {
        Entity entity = worldState.entitiesById.get(entityId);
        if (entity == null) return;
        if (entity.ownerNationId == null || !entity.ownerNationId.equals(nationId)) return;

        PlayerState player = worldState.playerManager.getPlayerState(playerId);
        if (player == null) return;

        entity.ownerPlayerId = playerId;
        player.addOwnedEntity(entity);
        worldState.markRealtimeDirty();
    }

    // ============================
    //  批量操作
    // ============================

    /**
     * 批量分配实体给玩家。
     *
     * @param entityIds 实体ID集合
     * @param playerId  目标玩家ID
     */
    public void assignToPlayer(Collection<Long> entityIds, String playerId) {
        if (entityIds == null) return;
        for (Long id : entityIds) {
            if (id != null) assignToPlayer(id, playerId);
        }
    }

    /**
     * 批量分配实体给国家。
     *
     * @param entityIds 实体ID集合
     * @param nationId  目标国家ID
     */
    public void assignToNation(Collection<Long> entityIds, String nationId) {
        if (entityIds == null) return;
        for (Long id : entityIds) {
            if (id != null) assignToNation(id, nationId);
        }
    }

    // ============================
    //  查询
    // ============================

    /**
     * 查询玩家直接拥有的实体ID集合。
     *
     * @param playerId 玩家ID
     * @param type     实体类型
     * @return 实体ID集合（只读）
     */
    public Set<Long> getPlayerOwnedEntityIds(String playerId, EntityType type) {
        if (playerId == null || type == null) return Collections.emptySet();
        PlayerState player = worldState.playerManager.getPlayerState(playerId);
        return player != null ? player.getOwnedEntityIds(type) : Collections.emptySet();
    }

    /**
     * 查询指定国家在某个实体类型下直接拥有的实体ID集合。
     *
     * @param nationId 国家ID
     * @param type     实体类型
     * @return 实体ID集合（只读）
     */
    public Set<Long> getNationOwnedEntityIds(String nationId, EntityType type) {
        if (nationId == null || type == null) return Collections.emptySet();
        NationState nation = worldState.nationManager.getNationState(nationId);
        return nation != null ? nation.getOwnedEntityIds(type) : Collections.emptySet();
    }
}
