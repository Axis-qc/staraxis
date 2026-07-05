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
 * SnapshotMessageFactory（精简 3D 版本）
 *
 * 负责将 game 模块的权威状态快照转换为 webnet 模块的 DTO 并打包为消息喵。
 * 已移除所有 hex SectorCoord 依赖。
 */
public final class SnapshotMessageFactory {

    public static final String SNAPSHOT_SYNC_MODE_FULL = "full";
    public static final String SNAPSHOT_SYNC_MODE_DELTA = "delta";

    private SnapshotMessageFactory() {}

    private static long lastLogTimeMs = 0;
    private static boolean hasLoggedEntityStatsOnce = false;

    /**
     * 构建包含实时状态与日结算状态的快照消息（支持系统可见性过滤）喵。
     */
    public static SnapshotMessageDto buildSnapshotMessageWithNation(StarAxisGameRuntime runtime, long tickCostMs,
            Set<Long> visibleSystemIds, String nationId) {
        runtime.publishRealtimeSnapshotIfNeeded();
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();

        Set<String> filterSystemKeys = visibleSystemIds != null
                ? visibleSystemIds.stream().map(String::valueOf).collect(Collectors.toSet())
                : new HashSet<>();

        Map<String, List<EntitySnapshot>> snapshotsBySystem = rt.getEntitySnapshotsBySystemView();
        List<EntitySnapshot> filteredPublicSnapshots = new ArrayList<>();
        Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel = new HashMap<>();

        boolean hasNationId = nationId != null && !nationId.isBlank();
        var intelSystem = runtime.getWorldStateForSimOnly().intelSystem;

        // 公开实体：无条件
        for (List<EntitySnapshot> sysSnapshots : snapshotsBySystem.values()) {
            for (EntitySnapshot s : sysSnapshots) {
                if (s.isPublic) filteredPublicSnapshots.add(s);
            }
        }

        // 私有实体：按可见性过滤
        Set<String> processKeys = !filterSystemKeys.isEmpty() ? filterSystemKeys : snapshotsBySystem.keySet();
        for (String systemKey : processKeys) {
            List<EntitySnapshot> snapshots = snapshotsBySystem.get(systemKey);
            if (snapshots == null || snapshots.isEmpty()) continue;

            int detectorLevel = -1;
            if (hasNationId && intelSystem != null) {
                for (EntitySnapshot se : snapshots) {
                    if (se.posWorldGU != null) {
                        detectorLevel = intelSystem.getEffectiveDetectorLevel3D(nationId, se.posWorldGU);
                        break;
                    }
                }
            }
            boolean hasDetector = detectorLevel >= 0;
            int cutoffIndex = hasDetector ? findCutoffIndex(snapshots, detectorLevel) : 0;

            for (int i = 0; i < snapshots.size(); i++) {
                EntitySnapshot s = snapshots.get(i);
                if (s.isPublic) continue;

                boolean visible = false;
                if (hasNationId) {
                    visible = nationId.equals(s.ownerNationId);
                    if (!visible && hasDetector && i <= cutoffIndex) visible = true;
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
                rt.getSectorOwnerNationIdByCoordView(),
                filteredPublicSnapshots, privateEntitiesByIntelLevel);

        DailySettlementState dailyActive = runtime.getDailySettlementStateBufferForReadonly().getActive();
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
            daily = new DailySettlementStateDto(dailyActive.settledAtGameSeconds,
                    dailyActive.settledDay, planetSurfaces, dailyActive.nationAssetsByNationId);
        }

        return new SnapshotMessageDto(true, null, realTime, daily, null, null,
                runtime.getWorldStateForSimOnly().nationManager.getPlayerNationId());
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
        return SnapshotHighFreqMessageDto.from(snapshot);
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
                rt.sectorOwnerNationIdByCoord,
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
        int starCount = runtime.getWorldStateForSimOnly().astro.getSystemsView().size();
        return new WorldSummaryDto(starCount, rt.worldRadius, rt.simulationTick);
    }

    public static String extractOwnerNationId(EntitySnapshot s) {
        return s != null ? s.ownerNationId : null;
    }

    private static int findCutoffIndex(List<EntitySnapshot> snapshots, int level) {
        int lo = 0, hi = snapshots.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (snapshots.get(mid).intelRequiredLevel <= level) lo = mid + 1;
            else hi = mid - 1;
        }
        return hi;
    }
}
