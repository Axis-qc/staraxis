package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnapshotLowFreqMessageDto {

    public final String type = "snapshot_low_freq";
    public final boolean ok;
    public final String error;
    public final long simulationTick;
    public final long version;
    /**
     * 同步模式喵："full"表示全量同步，"delta"表示增量同步喵。
     *
     * 全量同步喵：包含完整低频状态，用于初始连接或恢复同步喵。
     * 增量同步喵：仅包含自baseVersion以来的变化，需要客户端有正确的基线状态喵。
     */
    public final String syncMode;
    /**
     * 增量同步的基线版本号喵。
     *
     * 规则喵：
     * 1. 当syncMode为"delta"时，此字段必须提供喵。
     * 2. 客户端收到delta包时，必须检查baseVersion === lastAppliedLowFreqVersion喵。
     * 3. 如果基线不连续，客户端不能硬合并，必须请求低频全量重同步喵。
     */
    public final Long baseVersion;
    public final Integer worldRadius;
    public final String worldType;
    public final Double gameSecondsPerRealSecond;
    public final Double timeScale;
    public final Integer year;
    public final Integer month;
    public final Integer day;
    public final Integer hour;
    public final Integer minute;
    public final Integer second;
    public final List<SectorCenterDto> sectorCenters;
    public final Map<String, String> sectorOwnerNationIdByCoord;
    public final DailySettlementStateDto dailySettlementState;
    public final String playerNationId;

    public SnapshotLowFreqMessageDto(
            boolean ok,
            String error,
            long simulationTick,
            long version,
            String syncMode,
            Long baseVersion,
            Integer worldRadius,
            String worldType,
            Double gameSecondsPerRealSecond,
            Double timeScale,
            Integer year,
            Integer month,
            Integer day,
            Integer hour,
            Integer minute,
            Integer second,
            List<SectorCenterDto> sectorCenters,
            Map<String, String> sectorOwnerNationIdByCoord,
            DailySettlementStateDto dailySettlementState,
            String playerNationId) {
        this.ok = ok;
        this.error = error;
        this.simulationTick = simulationTick;
        this.version = version;
        this.syncMode = syncMode;
        this.baseVersion = baseVersion;
        this.worldRadius = worldRadius;
        this.worldType = worldType;
        this.gameSecondsPerRealSecond = gameSecondsPerRealSecond;
        this.timeScale = timeScale;
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.sectorCenters = sectorCenters;
        this.sectorOwnerNationIdByCoord = sectorOwnerNationIdByCoord;
        this.dailySettlementState = dailySettlementState;
        this.playerNationId = playerNationId;
    }

    public static SnapshotLowFreqMessageDto forFull(
            long simulationTick,
            long version,
            Integer worldRadius,
            String worldType,
            Double gameSecondsPerRealSecond,
            Double timeScale,
            Integer year,
            Integer month,
            Integer day,
            Integer hour,
            Integer minute,
            Integer second,
            List<SectorCenterDto> sectorCenters,
            Map<String, String> sectorOwnerNationIdByCoord,
            DailySettlementStateDto dailySettlementState,
            String playerNationId) {
        return new SnapshotLowFreqMessageDto(
                true,
                null,
                simulationTick,
                version,
                "full",
                null,
                worldRadius,
                worldType,
                gameSecondsPerRealSecond,
                timeScale,
                year,
                month,
                day,
                hour,
                minute,
                second,
                sectorCenters,
                sectorOwnerNationIdByCoord,
                dailySettlementState,
                playerNationId);
    }

    public static SnapshotLowFreqMessageDto forDelta(
            long simulationTick,
            long version,
            long baseVersion,
            Integer worldRadius,
            String worldType,
            Double gameSecondsPerRealSecond,
            Double timeScale,
            Integer year,
            Integer month,
            Integer day,
            Integer hour,
            Integer minute,
            Integer second,
            List<SectorCenterDto> sectorCenters,
            Map<String, String> sectorOwnerNationIdByCoord,
            DailySettlementStateDto dailySettlementState,
            String playerNationId) {
        return new SnapshotLowFreqMessageDto(
                true,
                null,
                simulationTick,
                version,
                "delta",
                baseVersion,
                worldRadius,
                worldType,
                gameSecondsPerRealSecond,
                timeScale,
                year,
                month,
                day,
                hour,
                minute,
                second,
                sectorCenters,
                sectorOwnerNationIdByCoord,
                dailySettlementState,
                playerNationId);
    }

    public static SnapshotLowFreqMessageDto forError(String error) {
        return new SnapshotLowFreqMessageDto(
                false,
                error,
                0L,
                0L,
                "full",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
