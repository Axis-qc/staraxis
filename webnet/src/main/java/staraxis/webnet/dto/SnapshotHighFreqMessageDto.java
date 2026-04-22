package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnapshotHighFreqMessageDto {

    public final String type = "snapshot_high_freq";
    public final boolean ok;
    public final String error;
    public final Long tickCostMs;
    public final long simulationTick;
    public final long totalGameSeconds;
    public final double totalGameSecondsExact;
    public final double deltaGameSeconds;
    /**
     * 同步模式喵："full"表示全量同步，"delta"表示增量同步喵。
     *
     * 全量同步喵：包含完整状态，用于初始连接或恢复同步喵。
     * 增量同步喵：仅包含自baseTick以来的变化，需要客户端有正确的基线状态喵。
     */
    public final String syncMode;
    /**
     * 增量同步的基线tick喵。
     *
     * 规则喵：
     * 1. 当syncMode为"delta"时，此字段必须提供喵。
     * 2. 客户端收到delta包时，必须检查baseTick === lastAppliedHighFreqTick喵。
     * 3. 如果基线不连续，客户端不能硬合并，必须触发一次高频全量重同步喵。
     * 4. 服务端应每隔固定tick发送高频keyframe（全量包），降低丢包恢复成本喵。
     */
    public final Long baseTick;
    public final List<EntitySnapshot> entities;
    public final Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel;
    public final String playerNationId;

    public SnapshotHighFreqMessageDto(
            boolean ok,
            String error,
            Long tickCostMs,
            long simulationTick,
            long totalGameSeconds,
            double totalGameSecondsExact,
            double deltaGameSeconds,
            String syncMode,
            Long baseTick,
            List<EntitySnapshot> entities,
            Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel,
            String playerNationId) {
        this.ok = ok;
        this.error = error;
        this.tickCostMs = tickCostMs;
        this.simulationTick = simulationTick;
        this.totalGameSeconds = totalGameSeconds;
        this.totalGameSecondsExact = totalGameSecondsExact;
        this.deltaGameSeconds = deltaGameSeconds;
        this.syncMode = syncMode;
        this.baseTick = baseTick;
        this.entities = entities;
        this.privateEntitiesByIntelLevel = privateEntitiesByIntelLevel;
        this.playerNationId = playerNationId;
    }

    public static SnapshotHighFreqMessageDto forFull(
            Long tickCostMs,
            long simulationTick,
            long totalGameSeconds,
            double totalGameSecondsExact,
            double deltaGameSeconds,
            List<EntitySnapshot> entities,
            Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel,
            String playerNationId) {
        return new SnapshotHighFreqMessageDto(
                true,
                null,
                tickCostMs,
                simulationTick,
                totalGameSeconds,
                totalGameSecondsExact,
                deltaGameSeconds,
                "full",
                null,
                entities,
                privateEntitiesByIntelLevel,
                playerNationId);
    }

    public static SnapshotHighFreqMessageDto forDelta(
            Long tickCostMs,
            long simulationTick,
            long totalGameSeconds,
            double totalGameSecondsExact,
            double deltaGameSeconds,
            long baseTick,
            List<EntitySnapshot> entities,
            Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel,
            String playerNationId) {
        return new SnapshotHighFreqMessageDto(
                true,
                null,
                tickCostMs,
                simulationTick,
                totalGameSeconds,
                totalGameSecondsExact,
                deltaGameSeconds,
                "delta",
                baseTick,
                entities,
                privateEntitiesByIntelLevel,
                playerNationId);
    }

    public static SnapshotHighFreqMessageDto forError(String error) {
        return new SnapshotHighFreqMessageDto(
                false,
                error,
                null,
                0L,
                0L,
                0.0,
                0.0,
                "full",
                null,
                null,
                null,
                null);
    }
}
