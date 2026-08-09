package staraxis.webnet.websocket;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.DailySettlementState.ExtractionFacilityDailySnapshot;
import staraxis.game.state.DailySettlementState.InventoryDailySnapshot;
import staraxis.game.state.DailySettlementState.PlanetSurfaceDailySnapshot;
import staraxis.game.state.DailySettlementState.ProcessingFacilityDailySnapshot;
import staraxis.game.state.DailySettlementState.SettlementReportDailySnapshot;
import staraxis.game.state.DailySettlementState.TransferDailySnapshot;
import staraxis.webnet.dto.CommandResultMessageDto;
import staraxis.webnet.dto.DailySettlementStateDto;
import staraxis.webnet.dto.RealTimeStateDto;
import staraxis.webnet.dto.SnapshotHighFreqMessageDto;
import staraxis.webnet.dto.SnapshotLowFreqMessageDto;
import staraxis.webnet.dto.SnapshotMessageDto;
import staraxis.webnet.dto.WorldSummaryDto;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SnapshotMessageFactory
 *
 * 负责将 game 模块的权威状态快照转换为 webnet 模块的 DTO 并打包为消息喵。
 * 公开实体从 DailySettlementState 读取，动态实体从 RealTimeWorldState 读取，情报等级用预计算字段。
 * 行星工业/物流/结算快照（G2.7）由 {@link #toDailySettlementStateDto(DailySettlementState)} 纯转换，
 * 只消费传入的 DailySettlementState，不读取 WorldState 喵。
 */
public final class SnapshotMessageFactory {

    public static final String SNAPSHOT_SYNC_MODE_FULL = "full";
    public static final String SNAPSHOT_SYNC_MODE_DELTA = "delta";

    private SnapshotMessageFactory() {}

    /**
     * 构建包含实时状态与日结算状态的快照消息（支持系统可见性过滤）喵。
     *
     * 改造后：
     * - 公开实体基线（STAR/PLANET/SYSTEM_BARYCENTER）从 DailySettlementState 读取
     * - 私有动态实体（SHIP）从 RealTimeWorldState 读取
     * - 情报等级从 DailySettlementState 预计算字段获取，不再读 WorldState
     * - 行星工业/物流/结算快照由 {@link #toDailySettlementStateDto(DailySettlementState)} 转换，
     *   只使用传入的活动 DailySettlementState，不读取 WorldState 喵
     */
    public static SnapshotMessageDto buildSnapshotMessageWithNation(StarAxisGameRuntime runtime, long tickCostMs,
            Set<Long> visibleSystemIds, String nationId) {
        runtime.publishRealtimeSnapshotIfNeeded();
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();
        DailySettlementState dailyActive = runtime.getDailySettlementStateBufferForReadonly().getActive();

        // 公开实体：合并低频基线（静态天体） + 高频动态实体中公开的部分
        List<EntitySnapshot> filteredPublicSnapshots = new ArrayList<>();
        if (dailyActive != null && dailyActive.publicEntityBaselinesBySectorKey != null) {
            for (List<EntitySnapshot> baselines : dailyActive.publicEntityBaselinesBySectorKey.values()) {
                filteredPublicSnapshots.addAll(baselines);
            }
        }
        Map<String, List<EntitySnapshot>> highFreqBySystem = rt.getEntitySnapshotsBySystemView();
        for (List<EntitySnapshot> sysSnapshots : highFreqBySystem.values()) {
            for (EntitySnapshot s : sysSnapshots) {
                if (s != null && s.isPublic) {
                    filteredPublicSnapshots.add(s);
                }
            }
        }

        // 私有实体筛选：可见性 + 情报等级（用预计算字段）
        Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel = new HashMap<>();
        boolean hasNationId = nationId != null && !nationId.isBlank();

        Map<String, Long> systemIdToDetectorLevel = null;
        if (hasNationId && dailyActive != null && dailyActive.detectorLevelByNationAndSystem != null) {
            Map<Long, Integer> nationDetector = dailyActive.detectorLevelByNationAndSystem.get(nationId);
            if (nationDetector != null) {
                systemIdToDetectorLevel = new HashMap<>();
                for (Map.Entry<Long, Integer> entry : nationDetector.entrySet()) {
                    systemIdToDetectorLevel.put(String.valueOf(entry.getKey()), (long) entry.getValue());
                }
            }
        }

        // 只处理高频快照中的私有实体（SHIP/STATION）
        Set<String> filterSystemKeys = visibleSystemIds != null
                ? visibleSystemIds.stream().map(String::valueOf).collect(Collectors.toSet())
                : highFreqBySystem.keySet();

        for (String systemKey : filterSystemKeys) {
            List<EntitySnapshot> snapshots = highFreqBySystem.get(systemKey);
            if (snapshots == null || snapshots.isEmpty()) continue;

            int detectorLevel = -1;
            if (systemIdToDetectorLevel != null && systemIdToDetectorLevel.containsKey(systemKey)) {
                detectorLevel = systemIdToDetectorLevel.get(systemKey).intValue();
            }
            boolean hasDetector = detectorLevel >= 0;

            for (EntitySnapshot s : snapshots) {
                if (s == null || s.isPublic) continue;

                boolean visible = false;
                if (hasNationId) {
                    visible = nationId.equals(s.ownerNationId);
                    if (!visible && hasDetector && s.intelRequiredLevel <= detectorLevel) visible = true;
                }
                if (!visible) continue;

                privateEntitiesByIntelLevel
                    .computeIfAbsent(s.intelRequiredLevel, k -> new ArrayList<>())
                    .add(s);
            }
        }

        RealTimeStateDto realTime = new RealTimeStateDto(
                rt.simulationTick, rt.totalGameSeconds, rt.totalGameSecondsExact, rt.deltaGameSeconds,
                rt.worldRadius, rt.worldType.name(), rt.gameSecondsPerRealSecond, rt.timeScale,
                rt.year, rt.month, rt.day, rt.hour, rt.minute, rt.second,
                filteredPublicSnapshots, privateEntitiesByIntelLevel);

        // 日结算快照转换：只消费传入的 dailyActive（不读 WorldState）
        DailySettlementStateDto daily = toDailySettlementStateDto(dailyActive);

        return new SnapshotMessageDto(true, null, tickCostMs, realTime, daily, nationId);
    }

    /**
     * 向后兼容的无国家ID版本。
     */
    public static SnapshotMessageDto buildSnapshotMessage(StarAxisGameRuntime runtime, long tickCostMs,
            Set<Long> visibleSystemIds) {
        return buildSnapshotMessageWithNation(runtime, tickCostMs, visibleSystemIds, null);
    }

    /**
     * 将传入的 DailySettlementState 纯转换为 DTO（不读取 WorldState）喵。
     *
     * 覆盖行星地表（地表区域/城市）与 G2.7 工业/物流/结算快照：
     * inventories / extractionFacilities / processingFacilities / inTransitTransfers /
     * lastSettlementReport 及其嵌套 Map/List 全部转换，保持与桌面本地一致的字段口径。
     * 空值约定与 game 侧一致：无库存/设施/运输时为稳定空集合，lastSettlementReport 为 null 表示未结算喵。
     *
     * @param daily 传入的权威低频基线快照（可为 null）
     * @return 转换后的 DTO；daily 为 null 时返回 null
     */
    public static DailySettlementStateDto toDailySettlementStateDto(DailySettlementState daily) {
        if (daily == null) {
            return null;
        }

        Map<Long, DailySettlementStateDto.PlanetSurfaceSnapshotDto> planetSurfaces = null;
        if (daily.planetSurfacesByPlanetId != null) {
            planetSurfaces = new HashMap<>();
            for (var entry : daily.planetSurfacesByPlanetId.entrySet()) {
                planetSurfaces.put(entry.getKey(), toPlanetSurfaceDto(entry.getKey(), entry.getValue()));
            }
        }

        // 转换 nationAssetsByNationId：EntityType enum -> String
        Map<String, Map<String, List<Long>>> nationAssetsStr = new HashMap<>();
        if (daily.nationAssetsByNationId != null) {
            for (var nationEntry : daily.nationAssetsByNationId.entrySet()) {
                Map<String, List<Long>> typeStrMap = new HashMap<>();
                for (var typeEntry : nationEntry.getValue().entrySet()) {
                    typeStrMap.put(typeEntry.getKey().name(), new ArrayList<>(typeEntry.getValue()));
                }
                nationAssetsStr.put(nationEntry.getKey(), typeStrMap);
            }
        }

        return new DailySettlementStateDto(
                daily.settledDay,
                daily.settledAtGameSeconds,
                daily.sectorCount,
                planetSurfaces,
                nationAssetsStr,
                daily.publicEntityBaselinesBySectorKey);
    }

    /**
     * 转换单个行星地表快照（含城市与工业只读快照）喵。
     */
    private static DailySettlementStateDto.PlanetSurfaceSnapshotDto toPlanetSurfaceDto(
            long planetEntityId, PlanetSurfaceDailySnapshot src) {
        List<DailySettlementStateDto.SurfaceRegionSnapshotDto> regions = src.surfaceRegions.stream()
                .map(r -> new DailySettlementStateDto.SurfaceRegionSnapshotDto(
                        r.regionId, r.regionType, r.name, r.surfacePercentage, r.developableSpaceRatio))
                .collect(Collectors.toList());

        List<DailySettlementStateDto.CitySnapshotDto> cities = src.cities.stream()
                .map(c -> new DailySettlementStateDto.CitySnapshotDto(
                        c.cityId, c.name, c.cityStage, c.cityScale, c.population, c.isPlanetaryCapital))
                .collect(Collectors.toList());

        List<DailySettlementStateDto.InventorySnapshotDto> inventories = src.inventories.stream()
                .map(SnapshotMessageFactory::toInventoryDto)
                .collect(Collectors.toList());

        List<DailySettlementStateDto.ExtractionFacilitySnapshotDto> extractionFacilities =
                src.extractionFacilities.stream()
                        .map(SnapshotMessageFactory::toExtractionFacilityDto)
                        .collect(Collectors.toList());

        List<DailySettlementStateDto.ProcessingFacilitySnapshotDto> processingFacilities =
                src.processingFacilities.stream()
                        .map(SnapshotMessageFactory::toProcessingFacilityDto)
                        .collect(Collectors.toList());

        List<DailySettlementStateDto.TransferSnapshotDto> inTransitTransfers = src.inTransitTransfers.stream()
                .map(SnapshotMessageFactory::toTransferDto)
                .collect(Collectors.toList());

        DailySettlementStateDto.SettlementReportSnapshotDto lastSettlementReport =
                src.lastSettlementReport != null ? toSettlementReportDto(src.lastSettlementReport) : null;

        return new DailySettlementStateDto.PlanetSurfaceSnapshotDto(
                planetEntityId, regions, cities, inventories, extractionFacilities,
                processingFacilities, inTransitTransfers, lastSettlementReport);
    }

    /**
     * 转换本地库存只读快照，substances / reservedAmounts 保持 game 迭代顺序喵。
     */
    private static DailySettlementStateDto.InventorySnapshotDto toInventoryDto(InventoryDailySnapshot src) {
        return new DailySettlementStateDto.InventorySnapshotDto(
                src.inventoryId, src.ownerEntityId, src.capacity, src.usedCapacity,
                copyAmounts(src.substances), copyAmounts(src.reservedAmounts));
    }

    /**
     * 转换采集设施只读快照喵。
     */
    private static DailySettlementStateDto.ExtractionFacilitySnapshotDto toExtractionFacilityDto(
            ExtractionFacilityDailySnapshot src) {
        return new DailySettlementStateDto.ExtractionFacilitySnapshotDto(
                src.facilityId, src.facilityType, src.inventoryId, src.locationEntityId,
                src.resourceId, src.amountPerDay, src.status, src.lastFailureReason);
    }

    /**
     * 转换加工设施只读快照喵。
     */
    private static DailySettlementStateDto.ProcessingFacilitySnapshotDto toProcessingFacilityDto(
            ProcessingFacilityDailySnapshot src) {
        return new DailySettlementStateDto.ProcessingFacilitySnapshotDto(
                src.facilityId, src.facilityType, src.inventoryId, src.locationEntityId,
                src.activeRecipeId, src.progressDays, src.progressRecipeId, src.status, src.lastFailureReason);
    }

    /**
     * 转换在途运输只读快照，goods 保持 game 迭代顺序喵。
     */
    private static DailySettlementStateDto.TransferSnapshotDto toTransferDto(TransferDailySnapshot src) {
        return new DailySettlementStateDto.TransferSnapshotDto(
                src.transferId, src.sourceInventoryId, src.targetInventoryId,
                copyAmounts(src.goods), src.status, src.departedAtTick, src.arrivedAtTick);
    }

    /**
     * 转换日结算报告只读快照（含嵌套采集/加工/运输结果）喵。
     */
    private static DailySettlementStateDto.SettlementReportSnapshotDto toSettlementReportDto(
            SettlementReportDailySnapshot src) {
        List<DailySettlementStateDto.ExtractionResultSnapshotDto> extractions = src.extractions.stream()
                .map(e -> new DailySettlementStateDto.ExtractionResultSnapshotDto(
                        e.facilityId, e.facilityType, e.resourceId, e.success, e.failureReason,
                        copyAmounts(e.extracted)))
                .collect(Collectors.toList());

        List<DailySettlementStateDto.FacilityResultSnapshotDto> facilities = src.facilities.stream()
                .map(f -> new DailySettlementStateDto.FacilityResultSnapshotDto(
                        f.facilityId, f.facilityType, f.recipeId, f.success, f.failureReason,
                        f.batchCount, copyAmounts(f.produced), copyAmounts(f.consumed)))
                .collect(Collectors.toList());

        List<DailySettlementStateDto.TransferResultSnapshotDto> transfers = src.transfers.stream()
                .map(t -> new DailySettlementStateDto.TransferResultSnapshotDto(
                        t.transferId, t.resultType, copyAmounts(t.goods)))
                .collect(Collectors.toList());

        return new DailySettlementStateDto.SettlementReportSnapshotDto(src.tick, extractions, facilities, transfers);
    }

    /**
     * 深拷贝物质数量表（substanceId -> 数量）。
     *
     * 保留源表迭代顺序（与 game LinkedHashMap 顺序一致，保证输出确定性），
     * null 或空时返回稳定空集合喵。
     */
    private static Map<String, Double> copyAmounts(Map<String, Double> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(source);
    }

    public static SnapshotMessageDto buildWorldNotCreatedMessage() {
        return SnapshotMessageDto.forError("world_not_created");
    }

    public static SnapshotHighFreqMessageDto buildHighFreqSnapshotMessage(SnapshotMessageDto snapshot) {
        if (snapshot == null || snapshot.realTimeWorldState == null) {
            return SnapshotHighFreqMessageDto.forFull(
                    null, 0, 0, 0, 0,
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyMap(),
                    null);
        }
        var rt = snapshot.realTimeWorldState;
        return SnapshotHighFreqMessageDto.forFull(
                snapshot.tickCostMs,
                rt.simulationTick,
                rt.totalGameSeconds,
                rt.totalGameSecondsExact,
                rt.deltaGameSeconds,
                rt.entities,
                rt.privateEntitiesByIntelLevel,
                snapshot.playerNationId);
    }

    public static SnapshotLowFreqMessageDto buildLowFreqSnapshotMessage(SnapshotMessageDto snapshot,
            boolean includeEntities) {
        if (snapshot == null || !snapshot.ok || snapshot.realTimeWorldState == null) {
            String err = snapshot == null ? "snapshot_missing" : snapshot.error;
            return SnapshotLowFreqMessageDto.forError(err == null ? "snapshot_invalid" : err);
        }
        RealTimeStateDto rt = snapshot.realTimeWorldState;
        long version = snapshot.dailySettlementState != null
                ? snapshot.dailySettlementState.settledAtGameSeconds
                : rt.simulationTick;
        List<EntitySnapshot> publicEntities = includeEntities && rt.entities != null ? rt.entities
                : java.util.Collections.emptyList();
        return SnapshotLowFreqMessageDto.forFull(
                rt.simulationTick, version, rt.worldRadius, rt.worldType,
                rt.gameSecondsPerRealSecond, rt.timeScale,
                rt.year, rt.month, rt.day, rt.hour, rt.minute, rt.second,
                snapshot.dailySettlementState, snapshot.playerNationId, publicEntities);
    }

    public static CommandResultMessageDto buildCommandResultMessage(
            String clientCommandId, long entityId, long simulationTick,
            String resultType, double gameSeconds, String reason, Map<String, Object> correctionData) {
        return new CommandResultMessageDto(clientCommandId, entityId, simulationTick,
                resultType, gameSeconds, reason, correctionData);
    }

    public static WorldSummaryDto buildWorldSummary(StarAxisGameRuntime runtime) {
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();
        WorldSummaryDto dto = new WorldSummaryDto();
        dto.gameDay = rt.day;
        dto.simulationTick = rt.simulationTick;
        return dto;
    }

    public static String extractOwnerNationId(EntitySnapshot s) {
        return s != null ? s.ownerNationId : null;
    }
}
