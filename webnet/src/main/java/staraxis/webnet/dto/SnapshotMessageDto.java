package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SnapshotMessageDto
 *
 * 发送给前端的快照消息的顶层 DTO，替代手拼 Map，实现完全类型安全。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnapshotMessageDto {

    public final String type = "snapshot";

    public final boolean ok;

    public final String error;

    public final Long tickCostMs;

    public final RealTimeStateDto realTimeWorldState;

    public final DailySettlementStateDto dailySettlementState;

    public SnapshotMessageDto(boolean ok, String error, Long tickCostMs, RealTimeStateDto realTimeWorldState,
            DailySettlementStateDto dailySettlementState) {
        this.ok = ok;
        this.error = error;
        this.tickCostMs = tickCostMs;
        this.realTimeWorldState = realTimeWorldState;
        this.dailySettlementState = dailySettlementState;
    }

    public static SnapshotMessageDto forSuccess(long tickCostMs, RealTimeStateDto realTimeWorldState,
            DailySettlementStateDto dailySettlementState) {
        return new SnapshotMessageDto(true, null, tickCostMs, realTimeWorldState, dailySettlementState);
    }

    public static SnapshotMessageDto forError(String error) {
        return new SnapshotMessageDto(false, error, null, null, null);
    }
}
