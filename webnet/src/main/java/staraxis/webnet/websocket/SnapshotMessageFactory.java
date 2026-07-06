package staraxis.webnet.websocket;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.DailySettlementState;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SnapshotMessageFactory
 *
 * 负责将 game 模块的权威状态快照转换为 webnet 模块的 DTO 并打包为消息喵。
 * 公开实体从 DailySettlementState 读取，动态实体从 RealTimeWorldState 读取，情报等级用预计算字段。
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

        DailySettlementStateDto daily = null;
        if (dailyActive != null) {
            Map<Long, DailySettlementStateDto.PlanetSurfaceSnapshotDto> planetSurfaces = null;
            if (dailyActive.planetSurfacesByPlanetId != null) {
                planetSurfaces = new HashMap<>();
                for (var entry : dailyActive.planetSurfacesByPlanetId.entrySet()) {
                    var src = entry.getValue();
                    List<DailySettlementStateDto.SurfaceRegionSnapshotDto> regions = src.surfaceRegions.stream()
                            .map(r -> new DailySettlementStateDto.SurfaceRegionSnapshotDto(
                                    r.regionId, r.regionType, r.name, r.surfacePercentage, r.developableSpaceRatio))
                            .collect(Collectors.toList());
                    planetSurfaces.put(entry.getKey(),
                            new DailySettlementStateDto.PlanetSurfaceSnapshotDto(entry.getKey(), regions));
                }
            }
            // 转换 nationAssetsByNationId：EntityType enum -> String
            Map<String, Map<String, List<Long>>> nationAssetsStr = new HashMap<>();
            if (dailyActive.nationAssetsByNationId != null) {
                for (var nationEntry : dailyActive.nationAssetsByNationId.entrySet()) {
                    Map<String, List<Long>> typeStrMap = new HashMap<>();
                    for (var typeEntry : nationEntry.getValue().entrySet()) {
                        typeStrMap.put(typeEntry.getKey().name(), new ArrayList<>(typeEntry.getValue()));
                    }
                    nationAssetsStr.put(nationEntry.getKey(), typeStrMap);
                }
            }

            daily = new DailySettlementStateDto(
                    dailyActive.settledDay,
                    dailyActive.settledAtGameSeconds,
                    dailyActive.sectorCount,
                    planetSurfaces,
                    nationAssetsStr,
                    dailyActive.publicEntityBaselinesBySectorKey);
        }

        return new SnapshotMessageDto(true, null, tickCostMs, realTime, daily, nationId);
    }

    /**
     * 向后兼容的无国家ID版本。
     */
    public static SnapshotMessageDto buildSnapshotMessage(StarAxisGameRuntime runtime, long tickCostMs,
            Set<Long> visibleSystemIds) {
        return buildSnapshotMessageWithNation(runtime, tickCostMs, visibleSystemIds, null);
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
