package staraxis.game.state;

import java.util.List;
import java.util.Map;

import staraxis.game.entity.EntityType;

/**
 * DailySettlementState
 *
 * 低频结算/基线快照（Low-Frequency Baseline Snapshot）：
 * - 原语义为“上一日结算状态”，现扩展为承载经济/生产/人口等低频数据的通用快照。
 * - 不再强绑定“日”这一时间粒度，后续可按固定时间窗口或事件触发生成。
 * - 仍保留 settledDay 字段以兼容现有调用，但推荐使用 settledAtGameSeconds 作为主时间口径喵。
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

    /**
     * 占位：星区总数（用于验证快照链路）。
     */
    public int sectorCount;

    /**
     * 行星地表（低频/静态）快照：Key 为 planetEntityId 喵。
     */
    public Map<Long, PlanetSurfaceDailySnapshot> planetSurfacesByPlanetId;

    /**
     * 国家资产基线快照：nationId -> (EntityType -> 该类型下实体ID列表) 喵。
     */
    public Map<String, Map<EntityType, List<Long>>> nationAssetsByNationId;

    public DailySettlementState() {
    }

    public void resetForFill() {
        settledDay = 0;
        settledAtGameSeconds = 0L;
        sectorCount = 0;
        planetSurfacesByPlanetId = null;
        nationAssetsByNationId = null;
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
