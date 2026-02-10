package staraxis.webnet.websocket;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.DailySettlementState;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;
import staraxis.webnet.dto.DailySettlementStateDto;
import staraxis.webnet.dto.RealTimeStateDto;
import staraxis.webnet.dto.SectorCenterDto;
import staraxis.webnet.dto.SnapshotMessageDto;
import staraxis.webnet.dto.WorldSummaryDto;
import staraxis.game.entity.EntityType;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SnapshotMessageFactory
 *
 * 负责将 game 模块的权威状态快照转换为 webnet 模块的 DTO 并打包为消息喵。
 */
public final class SnapshotMessageFactory {

    private SnapshotMessageFactory() {
    }

    /**
     * 构建星系宏观统计简报喵。
     */
    public static WorldSummaryDto buildWorldSummary(StarAxisGameRuntime runtime) {
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();
        WorldSummaryDto summary = new WorldSummaryDto();
        summary.gameDay = rt.gameDatetimeDay;
        summary.simulationTick = rt.simulationTick;
        summary.entityCounts = new HashMap<>();
        summary.nations = new HashMap<>();

        List<EntitySnapshot> snapshots = rt.getEntitySnapshotsView();
        for (EntitySnapshot s : snapshots) {
            String typeName = s.entityType.name();
            summary.entityCounts.put(typeName, summary.entityCounts.getOrDefault(typeName, 0) + 1);

            // 这里可以未来扩展更细致的国家统计逻辑喵
        }

        return summary;
    }

    /**
     * 构建包含实时状态与日结算状态的快照消息喵。
     *
     * @param runtime    游戏运行时引用喵。
     * @param tickCostMs 本次 tick 的耗时（毫秒）喵。
     * @return 封装好的快照消息 DTO 喵。
     */
    public static SnapshotMessageDto buildSnapshotMessage(StarAxisGameRuntime runtime, long tickCostMs) {
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();

        // 1. 转换实时星区中心数据喵
        List<SectorCenterDto> sectorCenters = new ArrayList<>(rt.getSectorCentersWorldGUView().size());
        for (Map.Entry<SectorCoord, Vec2d> e : rt.getSectorCentersWorldGUView().entrySet()) {
            SectorCoord c = e.getKey();
            Vec2d p = e.getValue();
            sectorCenters.add(new SectorCenterDto(c.q(), c.r(), p.x(), p.y()));
        }

        RealTimeStateDto realTime = new RealTimeStateDto(
                rt.simulationTick,
                rt.gameDatetimeDay,
                rt.accGameHoursInDay,
                rt.worldRadius,
                sectorCenters,
                rt.getEntitySnapshotsView());

        // 2. 转换日结算状态（含低频地表数据）喵
        DailySettlementState dailyActive = runtime.getDailySettlementStateBufferForReadonly().getActive();

        Map<Long, DailySettlementStateDto.PlanetSurfaceSnapshotDto> planetSurfaces = null;
        if (dailyActive.planetSurfacesByPlanetId != null) {
            planetSurfaces = new HashMap<>();
            for (Map.Entry<Long, DailySettlementState.PlanetSurfaceDailySnapshot> entry : dailyActive.planetSurfacesByPlanetId
                    .entrySet()) {
                DailySettlementState.PlanetSurfaceDailySnapshot source = entry.getValue();

                List<DailySettlementStateDto.SurfaceRegionSnapshotDto> regions = source.surfaceRegions.stream()
                        .map(r -> new DailySettlementStateDto.SurfaceRegionSnapshotDto(
                                r.regionId,
                                r.regionType,
                                r.name,
                                r.surfacePercentage,
                                r.developableSpaceRatio))
                        .collect(Collectors.toList());

                planetSurfaces.put(entry.getKey(),
                        new DailySettlementStateDto.PlanetSurfaceSnapshotDto(source.planetEntityId, regions));
            }
        }

        DailySettlementStateDto daily = new DailySettlementStateDto(
                dailyActive.settledDay,
                dailyActive.sectorCount,
                planetSurfaces);

        return SnapshotMessageDto.forSuccess(tickCostMs, realTime, daily);
    }

    /**
     * 构建世界未创建时的错误消息喵。
     */
    public static SnapshotMessageDto buildWorldNotCreatedMessage() {
        return SnapshotMessageDto.forError("world_not_created");
    }
}
