package staraxis.game.state;

import java.util.List;
import java.util.Map;
import java.util.Set;

import staraxis.game.entity.EntityType;
import staraxis.game.state.snapshot.EntitySnapshot;

/**
 * DailySettlementState
 *
 * 低频基线快照（Low-Frequency Baseline Snapshot）：
 * - 每 20 tick 发布一次，承载不需要每 tick 更新的数据。
 * - 包含：恒星/行星/卫星/重心 基线快照、国家资产表、行星地表数据、
 *   玩家→国家映射、各国家可见星系列表、各星系情报探测等级。
 * - 双缓冲 + volatile swap，外部只读。
 */
public class DailySettlementState {

    /**
     * 该快照对应的“已落账日序号”（历史兼容字段）。
     *
     * 说明：
     * - 在 1:1 时间模式下，不再以“日”为唯一结算粒度，后续可能仅作为统计/展示维度使用。
     */
    public int settledDay;

    /**
     * 该快照生成时的游戏总秒数（更通用的时间口径）。
     */
    public long settledAtGameSeconds;

    /** 星区总数（用于验证快照链路）。 */
    public int sectorCount;

    /** 该快照对应的 simulationTick（主要版本号）。 */
    public long baselineTick;

    /** 行星地表（低频/静态）快照：Key 为 planetEntityId 喵。 */
    public Map<Long, PlanetSurfaceDailySnapshot> planetSurfacesByPlanetId;

    /** 国家资产基线快照：nationId -> (EntityType -> 该类型下实体ID列表) 喵。 */
    public Map<String, Map<EntityType, List<Long>>> nationAssetsByNationId;

    /** 公开实体基线快照（按恒星系聚合）：systemId字符串 -> 公开实体快照列表 喵。 */
    public Map<String, List<EntitySnapshot>> publicEntityBaselinesBySectorKey;

    /** 玩家→国家映射（playerId -> nationId）。供 webnet 查询玩家所属国家。 */
    public Map<String, String> playerToNationMap;

    /** 国家→玩家列表映射（nationId -> playerId 列表）。 */
    public Map<String, List<String>> nationToPlayerIdsMap;

    /** 各国家的可见星系 ID 列表（预计算，替代 visibilitySystem 实时查询）。 */
    public Map<String, Set<Long>> visibleSystemIdsByNationId;

    /** 各星系对各国家的探测等级（预计算，替代 intelSystem 实时查询）。
     *  结构: nationId -> (systemId -> detectorLevel) */
    public Map<String, Map<Long, Integer>> detectorLevelByNationAndSystem;

    public DailySettlementState() {
    }

    public void resetForFill() {
        settledDay = 0;
        settledAtGameSeconds = 0L;
        sectorCount = 0;
        baselineTick = 0L;
        planetSurfacesByPlanetId = null;
        nationAssetsByNationId = null;
        publicEntityBaselinesBySectorKey = null;
        playerToNationMap = null;
        nationToPlayerIdsMap = null;
        visibleSystemIdsByNationId = null;
        detectorLevelByNationAndSystem = null;
    }

    /**
     * PlanetSurfaceDailySnapshot（行星地表日快照）喵。
     */
    public static class PlanetSurfaceDailySnapshot {
        public final long planetEntityId;
        public final List<SurfaceRegionDailySnapshot> surfaceRegions;

        public PlanetSurfaceDailySnapshot(long planetEntityId, List<SurfaceRegionDailySnapshot> surfaceRegions) {
            this.planetEntityId = planetEntityId;
            this.surfaceRegions = surfaceRegions;
        }
    }

    /**
     * SurfaceRegionDailySnapshot（地表区域日快照）喵。
     */
    public static class SurfaceRegionDailySnapshot {
        public final long regionId;
        public final String regionType;
        public final String name;
        public final double surfacePercentage;
        public final double developableSpaceRatio;

        public SurfaceRegionDailySnapshot(long regionId, String regionType, String name, double surfacePercentage,
                double developableSpaceRatio) {
            this.regionId = regionId;
            this.regionType = regionType;
            this.name = name;
            this.surfacePercentage = surfacePercentage;
            this.developableSpaceRatio = developableSpaceRatio;
        }
    }
}
