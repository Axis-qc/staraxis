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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * @param runtime        游戏运行时引用喵。
     * @param tickCostMs     本次 tick 的耗时（毫秒）喵。
     * @param visibleSectors 可见星区集合（用于过滤实体），若为 null 则返回不含实体列表的概要喵。
     * @return 封装好的快照消息 DTO 喵。
     */
    public static SnapshotMessageDto buildSnapshotMessage(StarAxisGameRuntime runtime, long tickCostMs,
            Set<SectorCoord> visibleSectors) {
        // 调用新方法，传入 null 国家ID（保持向后兼容）喵。
        return buildSnapshotMessageWithNation(runtime, tickCostMs, visibleSectors, null);
    }

    /**
     * 构建世界未创建时的错误消息喵。
     */
    public static SnapshotMessageDto buildWorldNotCreatedMessage() {
        return SnapshotMessageDto.forError("world_not_created");
    }

    private static long lastLogTimeMs = 0;
    private static boolean hasLoggedEntityStatsOnce = false;

    /**
     * 构建包含实时状态与日结算状态的快照消息（支持国家可见性过滤）喵。
     *
     * @param runtime        游戏运行时引用喵。
     * @param tickCostMs     本次 tick 的耗时（毫秒）喵。
     * @param visibleSectors 可见星区集合（用于过滤实体），若为 null 则返回不含实体列表的概要喵。
     * @param nationId       玩家国家ID，用于可见性过滤，若为 null 则使用传统过滤逻辑喵。
     * @return 封装好的快照消息 DTO 喵。
     */
    public static SnapshotMessageDto buildSnapshotMessageWithNation(StarAxisGameRuntime runtime, long tickCostMs,
            Set<SectorCoord> visibleSectors, String nationId) {
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();

        // 1. 转换实时星区中心数据（仅包含订阅的星区）喵
        List<SectorCenterDto> sectorCenters = new ArrayList<>();
        Map<SectorCoord, Vec2d> allCenters = rt.getSectorCentersWorldGUView();

        if (visibleSectors != null && !visibleSectors.isEmpty()) {
            // 有可见星区：只发送客户端上报的可见星区喵
            for (SectorCoord sc : visibleSectors) {
                Vec2d p = allCenters.get(sc);
                if (p != null) {
                    sectorCenters.add(new SectorCenterDto(sc.q(), sc.r(), p.x(), p.y()));
                }
            }
        } else {
            // 没有可见星区或客户端未上报：发送一个默认的可见星区集合（中心附近）喵
            // 这确保新游戏开始时前端能看到星区数据喵
            int defaultRadius = 3; // 默认发送中心附近3个环的星区
            for (Map.Entry<SectorCoord, Vec2d> entry : allCenters.entrySet()) {
                SectorCoord coord = entry.getKey();
                // 修正：使用六边形距离计算或简单的范围判定喵
                if (Math.abs(coord.q()) <= defaultRadius && Math.abs(coord.r()) <= defaultRadius
                        && Math.abs(coord.q() + coord.r()) <= defaultRadius) {
                    Vec2d p = entry.getValue();
                    sectorCenters.add(new SectorCenterDto(coord.q(), coord.r(), p.x(), p.y()));
                }
            }
        }

        // 为实体过滤创建实际的可见星区集合喵
        Set<SectorCoord> filterSectors = visibleSectors;
        if (filterSectors == null || filterSectors.isEmpty()) {
            // 如果没有可见星区，使用默认星区集合进行过滤喵
            filterSectors = new HashSet<>();
            for (SectorCenterDto sc : sectorCenters) {
                filterSectors.add(new SectorCoord(sc.q, sc.r));
            }
        }

        // 调试日志：降低频率至每分钟一次喵
        long now = System.currentTimeMillis();
        if (now - lastLogTimeMs >= 60000) {
            lastLogTimeMs = now;
            System.out.println("[SnapshotMessageFactory] sectorCenters count: " + sectorCenters.size() +
                    ", filterSectors count: " + filterSectors.size() +
                    ", visibleSectors was " + (visibleSectors == null ? "null" : "size=" + visibleSectors.size()) +
                    ", nationId=" + nationId + " 喵");
        }

        // 2. 过滤实体快照喵
        List<EntitySnapshot> allSnapshots = rt.getEntitySnapshotsView();
        List<EntitySnapshot> filteredSnapshots = new ArrayList<>();

        // 实体统计日志：首次与每分钟一次喵
        long logNow = System.currentTimeMillis();
        boolean shouldLogEntityStats = !hasLoggedEntityStatsOnce || (logNow - lastLogTimeMs >= 60000);

        Map<String, Integer> allTypeCounts = null;
        Map<String, Integer> filteredTypeCounts = null;
        if (shouldLogEntityStats) {
            allTypeCounts = new HashMap<>();
            filteredTypeCounts = new HashMap<>();
            for (EntitySnapshot s : allSnapshots) {
                String tn = s.entityType == null ? "null" : s.entityType.name();
                allTypeCounts.put(tn, allTypeCounts.getOrDefault(tn, 0) + 1);
            }
        }

        if (filterSectors != null && !filterSectors.isEmpty()) {
            for (EntitySnapshot s : allSnapshots) {
                // 本国实体强制推送：ownerNationId == nationId 时无视星区订阅与战争迷雾过滤喵
                String ownerNationId = extractOwnerNationId(s);
                boolean isOwnedByCurrentNation = nationId != null && !nationId.isBlank()
                        && ownerNationId != null && ownerNationId.equals(nationId);

                if (!isOwnedByCurrentNation) {
                    // 星区过滤：只包含可见星区内的实体喵。
                    if (!filterSectors.contains(s.sectorCoord)) {
                        continue;
                    }

                    // 国家视野过滤：公共天体不受视野限制；非天体实体必须对该 nationId 完全可见才下发喵。
                    boolean isNaturalBody = s.entityType == EntityType.STAR
                            || s.entityType == EntityType.PLANET
                            || s.entityType == EntityType.SYSTEM_BARYCENTER;

                    if (!isNaturalBody) {
                        // 未绑定国家：不下发任何非天体实体喵。
                        if (nationId == null || nationId.isBlank()) {
                            continue;
                        }

                        staraxis.game.entity.Entity e = runtime.getWorldStateForSimOnly().entitiesById.get(s.entityId);
                        String vis = runtime.getWorldStateForSimOnly().visibilitySystem.computeEntityVisibility(e,
                                nationId);
                        if (!"FULL".equals(vis)) {
                            continue;
                        }
                    }
                }

                filteredSnapshots.add(s);

                if (shouldLogEntityStats && filteredTypeCounts != null) {
                    String tn = s.entityType == null ? "null" : s.entityType.name();
                    filteredTypeCounts.put(tn, filteredTypeCounts.getOrDefault(tn, 0) + 1);
                }
            }
        }

        if (shouldLogEntityStats) {
            hasLoggedEntityStatsOnce = true;
            lastLogTimeMs = logNow;
            staraxis.webnet.core.WebNetLog.logThrottled("snapshot_entity_stats",
                    "[SnapshotMessageFactory] entityStats all=" + String.valueOf(allTypeCounts)
                            + " filtered=" + String.valueOf(filteredTypeCounts)
                            + " filterSectors="
                            + (filterSectors == null ? "null" : String.valueOf(filterSectors.size()))
                            + " nationId=" + nationId);
        }

        RealTimeStateDto realTime = new RealTimeStateDto(
                rt.simulationTick,
                rt.gameDatetimeDay,
                rt.accGameHoursInDay,
                rt.worldRadius,
                sectorCenters,
                rt.getSectorOwnerNationIdByCoordView(),
                filteredSnapshots);

        // 3. 转换日结算状态（含低频地表数据）喵
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

        return SnapshotMessageDto.forSuccess(tickCostMs, realTime, daily, nationId);
    }

    /**
     * 从 EntitySnapshot 中提取所有者国家ID喵。
     *
     * @param snapshot 实体快照
     * @return 国家ID，如果无法提取则返回 null
     */
    private static String extractOwnerNationId(EntitySnapshot snapshot) {
        if (snapshot.details == null) {
            return null;
        }

        // 检查 ShipDetails（独立类）
        if (snapshot.details instanceof staraxis.game.state.snapshot.ShipDetails) {
            staraxis.game.state.snapshot.ShipDetails shipDetails = (staraxis.game.state.snapshot.ShipDetails) snapshot.details;
            return shipDetails.ownerNationId;
        }

        // 检查 EntitySnapshot.StarDetails（内部类）
        if (snapshot.details instanceof EntitySnapshot.StarDetails) {
            EntitySnapshot.StarDetails starDetails = (EntitySnapshot.StarDetails) snapshot.details;
            return starDetails.ownerNationId;
        }

        // 检查 EntitySnapshot.PlanetDetails（内部类）
        if (snapshot.details instanceof EntitySnapshot.PlanetDetails) {
            EntitySnapshot.PlanetDetails planetDetails = (EntitySnapshot.PlanetDetails) snapshot.details;
            return planetDetails.ownerNationId;
        }

        // 其他实体类型（如 SystemBarycenterDetails）没有所有者国家ID喵。
        return null;
    }
}
