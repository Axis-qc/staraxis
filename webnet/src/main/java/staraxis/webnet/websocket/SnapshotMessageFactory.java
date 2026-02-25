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

        // 2. 过滤实体快照并按情报等级分层聚合喵
        List<EntitySnapshot> allSnapshots = rt.getEntitySnapshotsView();
        List<EntitySnapshot> filteredPublicSnapshots = new ArrayList<>();
        Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel = new HashMap<>();

        // 实体统计日志：首次与每分钟一次喵
        long logNow = System.currentTimeMillis();
        boolean shouldLogEntityStats = !hasLoggedEntityStatsOnce || (logNow - lastLogTimeMs >= 60000);

        Map<String, Integer> allTypeCounts = null;
        if (shouldLogEntityStats) {
            allTypeCounts = new HashMap<>();
            for (EntitySnapshot s : allSnapshots) {
                String tn = s.entityType == null ? "null" : s.entityType.name();
                allTypeCounts.put(tn, allTypeCounts.getOrDefault(tn, 0) + 1);
            }
        }

        // 获取情报系统引用喵
        staraxis.game.intel.IntelSystem intelSystem = runtime.getWorldStateForSimOnly().intelSystem;

        if (filterSectors != null && !filterSectors.isEmpty()) {
            for (EntitySnapshot s : allSnapshots) {
                // 2.1 公开实体处理：仅用于向后兼容，主要已由基线承担喵
                if (s.isPublic) {
                    if (filterSectors.contains(s.sectorCoord)) {
                        filteredPublicSnapshots.add(s);
                    }
                    continue;
                }

                // 2.2 私有实体处理喵
                if (nationId == null || nationId.isBlank()) {
                    continue; // 无国家ID不分发私有实体喵
                }

                // 2.2.1 基础可见性过滤（视野系统）喵
                staraxis.game.entity.Entity e = runtime.getWorldStateForSimOnly().entitiesById.get(s.entityId);
                if (e == null)
                    continue;

                // 本国实体强制通过，否则走视野计算喵
                boolean isOwnedBySelf = nationId.equals(s.ownerNationId);
                if (!isOwnedBySelf) {
                    if (!filterSectors.contains(s.sectorCoord))
                        continue;

                    String vis = runtime.getWorldStateForSimOnly().visibilitySystem.computeEntityVisibility(e,
                            nationId);
                    if (!"FULL".equals(vis))
                        continue;
                }

                // 2.2.2 情报等级过滤与分层聚合（按星区探测等级表查表）喵
                if (intelSystem != null) {
                    Map<String, Integer> sectorIntelLevels = intelSystem.getNationSectorIntelLevelsView(nationId);
                    int requiredLevel = intelSystem.getRequiredIntelLevel(s.entityType);

                    String sectorKey = "q:" + s.sectorCoord.q() + ",r:" + s.sectorCoord.r();
                    int detectorLevel = sectorIntelLevels.getOrDefault(sectorKey, -1);

                    // 仅当星区探测等级 >= 实体情报需求等级时，才下发该实体数据喵
                    if (detectorLevel >= requiredLevel) {
                        privateEntitiesByIntelLevel.computeIfAbsent(requiredLevel, k -> new ArrayList<>()).add(s);
                    }
                }
            }
        }

        if (shouldLogEntityStats) {
            hasLoggedEntityStatsOnce = true;
            lastLogTimeMs = logNow;

            int privateCount = 0;
            for (List<EntitySnapshot> tier : privateEntitiesByIntelLevel.values()) {
                if (tier != null)
                    privateCount += tier.size();
            }

            // 统计过滤原因（近似统计，用于定位“实体为0”问题）喵
            int skippedNoNationId = 0;
            int skippedEntityMissing = 0;
            int skippedNotInFilterSectors = 0;
            int skippedVisNotFull = 0;
            int skippedIntelInsufficient = 0;

            if (filterSectors != null && !filterSectors.isEmpty()) {
                for (EntitySnapshot s : allSnapshots) {
                    if (s == null)
                        continue;
                    if (s.isPublic) {
                        continue;
                    }
                    if (nationId == null || nationId.isBlank()) {
                        skippedNoNationId++;
                        continue;
                    }

                    staraxis.game.entity.Entity e = runtime.getWorldStateForSimOnly().entitiesById.get(s.entityId);
                    if (e == null) {
                        skippedEntityMissing++;
                        continue;
                    }

                    boolean isOwnedBySelf = nationId.equals(s.ownerNationId);
                    if (!isOwnedBySelf) {
                        if (!filterSectors.contains(s.sectorCoord)) {
                            skippedNotInFilterSectors++;
                            continue;
                        }
                        String vis = runtime.getWorldStateForSimOnly().visibilitySystem.computeEntityVisibility(e,
                                nationId);
                        if (!"FULL".equals(vis)) {
                            skippedVisNotFull++;
                            continue;
                        }
                    }

                    if (intelSystem != null) {
                        Map<String, Integer> sectorIntelLevels = intelSystem.getNationSectorIntelLevelsView(nationId);
                        int requiredLevel = intelSystem.getRequiredIntelLevel(s.entityType);
                        String sectorKey = "q:" + s.sectorCoord.q() + ",r:" + s.sectorCoord.r();
                        int detectorLevel = sectorIntelLevels.getOrDefault(sectorKey, -1);
                        if (detectorLevel < requiredLevel) {
                            skippedIntelInsufficient++;
                            continue;
                        }
                    } else {
                        skippedIntelInsufficient++;
                    }
                }
            }

            staraxis.webnet.core.WebNetLog.logThrottled("snapshot_entity_stats",
                    "[SnapshotMessageFactory] entityStats all=" + String.valueOf(allTypeCounts)
                            + " allSnapshots=" + allSnapshots.size()
                            + " filteredPublic=" + filteredPublicSnapshots.size()
                            + " privateCount=" + privateCount
                            + " privateTiers=" + privateEntitiesByIntelLevel.keySet()
                            + " filterSectors=" + (filterSectors == null ? "null" : String.valueOf(filterSectors.size()))
                            + " nationId=" + nationId
                            + " skippedNoNationId=" + skippedNoNationId
                            + " skippedEntityMissing=" + skippedEntityMissing
                            + " skippedNotInFilterSectors=" + skippedNotInFilterSectors
                            + " skippedVisNotFull=" + skippedVisNotFull
                            + " skippedIntelInsufficient=" + skippedIntelInsufficient);
        }

        RealTimeStateDto realTime = new RealTimeStateDto(
                rt.simulationTick,
                rt.gameDatetimeDay,
                rt.accGameHoursInDay,
                rt.worldRadius,
                rt.worldType.name(),
                rt.gameSecondsPerRealSecond,
                rt.timeScale,
                rt.year,
                rt.month,
                rt.day,
                rt.hour,
                rt.minute,
                rt.second,
                sectorCenters,
                rt.getSectorOwnerNationIdByCoordView(),
                filteredPublicSnapshots,
                privateEntitiesByIntelLevel);

        // 3. 转换低频基线快照（含低频地表与国家资产基线）喵
        DailySettlementState dailyActive = runtime.getDailySettlementStateBufferForReadonly().getActive();

        DailySettlementStateDto daily = null;
        if (dailyActive != null) {
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

            // 国家资产基线：EntityType -> String key 喵
            Map<String, Map<String, List<Long>>> nationAssetsByNationId = null;
            if (dailyActive.nationAssetsByNationId != null) {
                nationAssetsByNationId = new HashMap<>();
                for (Map.Entry<String, Map<EntityType, List<Long>>> e : dailyActive.nationAssetsByNationId.entrySet()) {
                    String nid = e.getKey();
                    Map<EntityType, List<Long>> source = e.getValue();
                    Map<String, List<Long>> target = new HashMap<>();
                    if (source != null) {
                        for (Map.Entry<EntityType, List<Long>> t : source.entrySet()) {
                            if (t.getKey() != null) {
                                target.put(t.getKey().name(), t.getValue());
                            }
                        }
                    }
                    nationAssetsByNationId.put(nid, target);
                }
            }

            daily = new DailySettlementStateDto(
                    dailyActive.settledDay,
                    dailyActive.settledAtGameSeconds,
                    dailyActive.sectorCount,
                    planetSurfaces,
                    nationAssetsByNationId,
                    dailyActive.publicEntityBaselinesBySectorKey);
        }

        return SnapshotMessageDto.forSuccess(tickCostMs, realTime, daily, nationId);
    }

    /**
     * 从 EntitySnapshot 中提取所有者国家ID喵。
     *
     * @param snapshot 实体快照
     * @return 国家ID，如果无法提取则返回 null
     */
    public static String extractOwnerNationId(EntitySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.ownerNationId;
    }
}
