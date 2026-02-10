package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * DailySettlementStateDto
 *
 * 对应 game 侧的 DailySettlementState，用于向前端传输低频/结算数据喵。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailySettlementStateDto {

    public final int settledDay;
    public final int sectorCount;

    /** 行星地表（低频/静态）快照：Key 为 planetEntityId 喵。 */
    public final Map<Long, PlanetSurfaceSnapshotDto> planetSurfaces;

    public DailySettlementStateDto(int settledDay, int sectorCount,
            Map<Long, PlanetSurfaceSnapshotDto> planetSurfaces) {
        this.settledDay = settledDay;
        this.sectorCount = sectorCount;
        this.planetSurfaces = planetSurfaces;
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
