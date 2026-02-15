package staraxis.game.state;

import staraxis.game.astro.AstroData;
import staraxis.game.entity.Entity;
import staraxis.game.sim.SimulationTime;
import staraxis.game.world.WorldMap;
import staraxis.game.world.WorldSector;
import staraxis.game.world.hex.SectorCoord;
import staraxis.game.nation.NationManager;
import staraxis.game.nation.NationSpawnService;
import staraxis.game.nation.VisibilitySystem;

import java.util.HashMap;
import java.util.Map;

/**
 * WorldState
 *
 * 游戏运行时的唯一权威世界状态容器（只允许模拟层读写）。
 */
public class WorldState {

    public final SimulationTime time;

    public final WorldMap worldMap;

    /**
     * 权威星体数据（恒星系、恒星、行星等）：仅允许模拟层读写。
     */
    public final AstroData astro;

    /**
     * 上一次低频基线快照发布时的游戏总秒数。
     */
    public long lastBaselinePublishGameSeconds = 0;

    /**
     * 低频快照是否因数据变化而变脏（需要尽快推送）。
     */
    public boolean baselineDirty = false;

    /**
     * 实体总表（entityId -> Entity）。
     */
    public final Map<Long, Entity> entitiesById = new HashMap<>();

    /**
     * 空间索引（entityId -> sectorCoord）。
     */
    public final Map<Long, SectorCoord> entitySectorById = new HashMap<>();

    /**
     * 国家管理器：管理所有国家的运行时状态、玩家归属和外交关系。
     */
    public final NationManager nationManager = new NationManager();

    /**
     * 国家资产管理器：集中管理国家与实体之间的资产归属关系。
     */
    public final staraxis.game.nation.NationAssetManager nationAssetManager = new staraxis.game.nation.NationAssetManager(
            this);

    /**
     * 国家出生点服务：根据出生策略为国家选择并分配初始星系与首都星球。
     */
    public final NationSpawnService nationSpawnService = new NationSpawnService(this);

    /**
     * 可见性系统：计算每个国家的可见性状态。
     */
    public final VisibilitySystem visibilitySystem = new VisibilitySystem(this);

    public WorldState(SimulationTime time, WorldMap worldMap, AstroData astro) {
        this.time = time;
        this.worldMap = worldMap;
        this.astro = astro;
    }

    public void registerEntity(Entity entity) {
        if (entity == null) {
            return;
        }

        entitiesById.put(entity.entityId, entity);

        if (entity.sectorCoord != null) {
            entitySectorById.put(entity.entityId, entity.sectorCoord);

            WorldSector sector = worldMap.getSector(entity.sectorCoord);
            if (sector != null) {
                sector.entityIds.add(entity.entityId);
            }
        }
    }

    public void moveEntityToSector(long entityId, SectorCoord nextSectorCoord) {
        Entity e = entitiesById.get(entityId);
        if (e == null) {
            return;
        }

        SectorCoord prev = entitySectorById.get(entityId);
        if (prev != null) {
            WorldSector prevSector = worldMap.getSector(prev);
            if (prevSector != null) {
                prevSector.entityIds.remove(entityId);
            }
        }

        e.sectorCoord = nextSectorCoord;
        entitySectorById.put(entityId, nextSectorCoord);

        if (nextSectorCoord != null) {
            WorldSector nextSector = worldMap.getSector(nextSectorCoord);
            if (nextSector != null) {
                nextSector.entityIds.add(entityId);
            }
        }
    }
}
