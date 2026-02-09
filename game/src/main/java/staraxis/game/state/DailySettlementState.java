package staraxis.game.state;

import java.util.List;
import java.util.Map;

/**
 * DailySettlementState
 *
 * 上一日结算状态（只读快照）：用于 UI 展示经济/生产/人口等具有固定结算周期（按“日”）的数据。
 * 也可承载低频/静态数据的“每日全量快照”，避免将静态数据塞入每 tick 的 RealTimeWorldState 喵。
 */
public class DailySettlementState {

    /**
     * 该快照对应的“已落账日序号”（上一日）。
     */
    public int settledDay;

    /**
     * 占位：星区总数（用于验证快照链路）。
     */
    public int sectorCount;

    /**
     * 行星地表（低频/静态）快照：Key 为 planetEntityId 喵。
     */
    public Map<Long, PlanetSurfaceDailySnapshot> planetSurfacesByPlanetId;

    public DailySettlementState() {
    }

    public void resetForFill() {
        settledDay = 0;
        sectorCount = 0;
        planetSurfacesByPlanetId = null;
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
