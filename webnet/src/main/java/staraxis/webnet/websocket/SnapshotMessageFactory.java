package staraxis.webnet.websocket;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.DailySettlementState;
import staraxis.game.world.hex.SectorCoord;
import staraxis.webnet.dto.CommandResultMessageDto;
import staraxis.webnet.dto.DailySettlementStateDto;
import staraxis.webnet.dto.RealTimeStateDto;
import staraxis.webnet.dto.SnapshotHighFreqMessageDto;
import staraxis.webnet.dto.SnapshotLowFreqMessageDto;
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

    public static final String SNAPSHOT_SYNC_MODE_FULL = "full";
    public static final String SNAPSHOT_SYNC_MODE_DELTA = "delta";

    private SnapshotMessageFactory() {
    }

    /**
     * 构建星系宏观统计简报喵。
     */
    public static WorldSummaryDto buildWorldSummary(StarAxisGameRuntime runtime) {
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();
        WorldSummaryDto summary = new WorldSummaryDto();
        // 统一以权威时间轴总秒数推导 Day，避免依赖已移除的旧字段喵。
        summary.gameDay = (int) (rt.totalGameSeconds / 86400L) + 1;
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
        runtime.publishRealtimeSnapshotIfNeeded();
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();

        // 为实体过滤创建实际的可见星区集合喵
        Set<SectorCoord> filterSectors = visibleSectors != null ? visibleSectors : new HashSet<>();

        // 2. 过滤实体快照并按情报等级分层聚合喵
        // 使用预先构建的按星区组织的快照索引喵
        Map<SectorCoord, List<EntitySnapshot>> snapshotsBySector = rt.getEntitySnapshotsBySectorView();
        List<EntitySnapshot> filteredPublicSnapshots = new ArrayList<>();
        Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel = new HashMap<>();

        // 实体统计日志：首次与每分钟一次喵
        long logNow = System.currentTimeMillis();
        boolean shouldLogEntityStats = !hasLoggedEntityStatsOnce || (logNow - lastLogTimeMs >= 60000);

        Map<String, Integer> allTypeCounts = null;
        if (shouldLogEntityStats) {
            allTypeCounts = new HashMap<>();
            // 统计所有实体数量喵
            for (List<EntitySnapshot> sectorSnapshots : snapshotsBySector.values()) {
                for (EntitySnapshot s : sectorSnapshots) {
                    String tn = s.entityType == null ? "null" : s.entityType.name();
                    allTypeCounts.put(tn, allTypeCounts.getOrDefault(tn, 0) + 1);
                }
            }
        }

        // 获取情报系统引用喵
        staraxis.game.intel.IntelSystem intelSystem = runtime.getWorldStateForSimOnly().intelSystem;

        // 2.2 公开实体处理：天文数据无条件推送给所有国家喵
        for (List<EntitySnapshot> sectorSnapshots : snapshotsBySector.values()) {
            for (EntitySnapshot s : sectorSnapshots) {
                if (s.isPublic) {
                    filteredPublicSnapshots.add(s);
                }
            }
        }

        // 2.3 私有实体处理：使用星区缓存的探测等级 + 二分查找快速裁剪喵
        // 优化点：
        // - 直接从星区读取 nationDetectorLevels，O(1) 查询喵
        // - 实体列表已按 intelRequiredLevel 排序，二分查找确定 cutoff 点喵
        // - 复杂度从 O(Entities) 降至 O(VisibleSectors * log(EntitiesPerSector)) 喵
        boolean hasNationId = nationId != null && !nationId.isBlank();
        if (snapshotsBySector != null && !snapshotsBySector.isEmpty()) {
            // 确定要遍历的星区：如果filterSectors非空则用它，否则遍历所有星区喵
            Set<SectorCoord> sectorsToProcess = (filterSectors != null && !filterSectors.isEmpty()) ? filterSectors
                    : snapshotsBySector.keySet();

            for (SectorCoord sector : sectorsToProcess) {
                List<EntitySnapshot> sectorSnapshots = snapshotsBySector.get(sector);
                if (sectorSnapshots == null || sectorSnapshots.isEmpty()) {
                    continue;
                }

                // O(1) 从星区缓存获取玩家在此星区的探测等级喵
                var worldSector = runtime.getWorldStateForSimOnly().worldMap.getSector(sector);
                int detectorLevel = (worldSector != null && hasNationId) ? worldSector.getDetectorLevel(nationId) : -1;

                // 如果该玩家在此星区无探测，只处理本国实体喵
                boolean hasDetector = detectorLevel >= 0;

                // 实体列表已按 intelRequiredLevel 排序喵
                // 使用二分查找找到 cutoff 点：最后一个 intelRequiredLevel <= detectorLevel 的索引喵
                int cutoffIndex = hasDetector ? findCutoffIndex(sectorSnapshots, detectorLevel) : 0;

                for (int i = 0; i < sectorSnapshots.size(); i++) {
                    EntitySnapshot s = sectorSnapshots.get(i);

                    // 跳过公开实体喵
                    if (s.isPublic) {
                        continue;
                    }

                    // 本国实体强制可见（无论探测等级）喵
                    boolean isOwnedBySelf = hasNationId && nationId.equals(s.ownerNationId);
                    if (isOwnedBySelf) {
                        privateEntitiesByIntelLevel.computeIfAbsent(s.intelRequiredLevel, k -> new ArrayList<>())
                                .add(s);
                        continue;
                    }

                    // 非本国实体：需要在该星区有探测，且情报等级 <= 探测等级喵
                    // 由于列表已排序，i < cutoffIndex 的实体都满足条件喵
                    if (hasDetector && i < cutoffIndex) {
                        privateEntitiesByIntelLevel.computeIfAbsent(s.intelRequiredLevel, k -> new ArrayList<>())
                                .add(s);
                    }
                }
            }
        }

        if (shouldLogEntityStats) {
            hasLoggedEntityStatsOnce = true;
            lastLogTimeMs = logNow;

            // 计算所有快照总数喵
            int totalSnapshotCount = 0;
            for (List<EntitySnapshot> sectorSnapshots : snapshotsBySector.values()) {
                totalSnapshotCount += sectorSnapshots.size();
            }

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
                // 遍历所有星区的快照喵
                for (List<EntitySnapshot> sectorSnapshots : snapshotsBySector.values()) {
                    for (EntitySnapshot s : sectorSnapshots) {
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
                            Map<String, Integer> sectorIntelLevels = intelSystem
                                    .getNationSectorIntelLevelsView(nationId);
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
            }

            staraxis.webnet.core.WebNetLog.logThrottled("snapshot_entity_stats",
                    "[SnapshotMessageFactory] entityStats all=" + String.valueOf(allTypeCounts)
                            + " allSnapshots=" + totalSnapshotCount
                            + " filteredPublic=" + filteredPublicSnapshots.size()
                            + " privateCount=" + privateCount
                            + " privateTiers=" + privateEntitiesByIntelLevel.keySet()
                            + " filterSectors="
                            + (filterSectors == null ? "null" : String.valueOf(filterSectors.size()))
                            + " nationId=" + nationId
                            + " skippedNoNationId=" + skippedNoNationId
                            + " skippedEntityMissing=" + skippedEntityMissing
                            + " skippedNotInFilterSectors=" + skippedNotInFilterSectors
                            + " skippedVisNotFull=" + skippedVisNotFull
                            + " skippedIntelInsufficient=" + skippedIntelInsufficient);
        }

        RealTimeStateDto realTime = new RealTimeStateDto(
                rt.simulationTick,
                rt.totalGameSeconds,
                rt.totalGameSecondsExact,
                rt.deltaGameSeconds,
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

    public static SnapshotHighFreqMessageDto buildHighFreqSnapshotMessage(SnapshotMessageDto legacySnapshot) {
        if (legacySnapshot == null || !legacySnapshot.ok || legacySnapshot.realTimeWorldState == null) {
            String error = legacySnapshot == null ? "snapshot_missing" : legacySnapshot.error;
            return SnapshotHighFreqMessageDto.forError(error == null ? "snapshot_invalid" : error);
        }

        RealTimeStateDto rt = legacySnapshot.realTimeWorldState;
        // 高频快照只发动态实体（舰船等），不发静态天体喵
        return SnapshotHighFreqMessageDto.forFull(
                legacySnapshot.tickCostMs,
                rt.simulationTick,
                rt.totalGameSeconds,
                rt.totalGameSecondsExact,
                rt.deltaGameSeconds,
                null, // 不再包含公开实体（恒星/行星/重心）
                rt.privateEntitiesByIntelLevel,
                legacySnapshot.playerNationId);
    }

    public static SnapshotLowFreqMessageDto buildLowFreqSnapshotMessage(SnapshotMessageDto legacySnapshot,
            boolean includeEntities) {
        if (legacySnapshot == null || !legacySnapshot.ok || legacySnapshot.realTimeWorldState == null) {
            String error = legacySnapshot == null ? "snapshot_missing" : legacySnapshot.error;
            return SnapshotLowFreqMessageDto.forError(error == null ? "snapshot_invalid" : error);
        }

        RealTimeStateDto rt = legacySnapshot.realTimeWorldState;
        long version = legacySnapshot.dailySettlementState != null
                ? legacySnapshot.dailySettlementState.settledAtGameSeconds
                : rt.simulationTick;
        // 低频快照携带公开实体基线（恒星/行星/重心），由调用方决定是否包含喵
        List<EntitySnapshot> publicEntities = includeEntities && rt.entities != null ? rt.entities
                : java.util.Collections.emptyList();
        return SnapshotLowFreqMessageDto.forFull(
                rt.simulationTick,
                version,
                rt.worldRadius,
                rt.worldType,
                rt.gameSecondsPerRealSecond,
                rt.timeScale,
                rt.year,
                rt.month,
                rt.day,
                rt.hour,
                rt.minute,
                rt.second,
                rt.sectorOwnerNationIdByCoord,
                legacySnapshot.dailySettlementState,
                legacySnapshot.playerNationId,
                publicEntities);
    }

    public static CommandResultMessageDto buildCommandResultMessage(
            String clientCommandId,
            long entityId,
            long simulationTick,
            String resultType,
            double gameSeconds,
            String reason,
            Map<String, Object> correctionData) {
        return new CommandResultMessageDto(
                clientCommandId,
                entityId,
                simulationTick,
                resultType,
                gameSeconds,
                reason,
                correctionData);
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

    /**
     * 二分查找：找到最后一个 intelRequiredLevel <= detectorLevel 的索引+1喵。
     *
     * 说明：
     * - 实体列表已按 intelRequiredLevel 升序排序喵。
     * - 返回值为 cutoffIndex，表示 [0, cutoffIndex) 范围内的实体都可见喵。
     * - 例如：实体等级 [0,0,1,1,3,6]，detectorLevel=1，返回 4（前4个可见）喵。
     *
     * @param snapshots     已排序的实体快照列表
     * @param detectorLevel 玩家在该星区的探测等级
     * @return cutoffIndex，范围 [0, snapshots.size()]
     */
    private static int findCutoffIndex(List<EntitySnapshot> snapshots, int detectorLevel) {
        int left = 0;
        int right = snapshots.size();
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (snapshots.get(mid).intelRequiredLevel <= detectorLevel) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return left;
    }
}
