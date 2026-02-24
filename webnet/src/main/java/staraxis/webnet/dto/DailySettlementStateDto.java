package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;
import java.util.Map;

/**
 * DailySettlementStateDto
 *
 * 对应 game 侧的 DailySettlementState，用于向前端传输低频/基线数据喵。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailySettlementStateDto {

    /** 历史兼容字段：日序号喵。 */
    public final int settledDay;

    /** 基线快照生成时的游戏总秒数喵。 */
    public final long settledAtGameSeconds;

    public final int sectorCount;

    /** 行星地表（低频/静态）快照：Key 为 planetEntityId 喵。 */
    public final Map<Long, PlanetSurfaceSnapshotDto> planetSurfaces;

    /** 国家资产基线快照：nationId -> (entityTypeName -> entityId 列表) 喵。 */
    public final Map<String, Map<String, List<Long>>> nationAssetsByNationId;

    /** 公开实体基线快照（按星区聚合）：sectorKey -> 公开实体快照列表 喵。 */
    public final Map<String, List<EntitySnapshot>> publicEntityBaselinesBySectorKey;

    public DailySettlementStateDto(int settledDay, long settledAtGameSeconds, int sectorCount,
            Map<Long, PlanetSurfaceSnapshotDto> planetSurfaces,
            Map<String, Map<String, List<Long>>> nationAssetsByNationId,
            Map<String, List<EntitySnapshot>> publicEntityBaselinesBySectorKey) {
        this.settledDay = settledDay;
        this.settledAtGameSeconds = settledAtGameSeconds;
        this.sectorCount = sectorCount;
        this.planetSurfaces = planetSurfaces;
        this.nationAssetsByNationId = nationAssetsByNationId;
        this.publicEntityBaselinesBySectorKey = publicEntityBaselinesBySectorKey;
    }

    /**
     * 行星地表快照 DTO 喵。
     */
    public static class PlanetSurfaceSnapshotDto {
        public final long planetEntityId;
        public final List<SurfaceRegionSnapshotDto> surfaceRegions;

        public PlanetSurfaceSnapshotDto(long planetEntityId, List<SurfaceRegionSnapshotDto> surfaceRegions) {
            this.planetEntityId = planetEntityId;
            this.surfaceRegions = surfaceRegions;
        }
    }

    /**
     * 地表区域快照 DTO 喵。
     */
    public static class SurfaceRegionSnapshotDto {
        public final long regionId;
        public final String regionType;
        public final String name;
        public final double surfacePercentage;
        public final double developableSpaceRatio;

        public SurfaceRegionSnapshotDto(long regionId, String regionType, String name, double surfacePercentage,
                double developableSpaceRatio) {
            this.regionId = regionId;
            this.regionType = regionType;
            this.name = name;
            this.surfacePercentage = surfacePercentage;
            this.developableSpaceRatio = developableSpaceRatio;
        }
    }
}
