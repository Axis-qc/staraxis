package staraxis.game.state;

/**
 * DailySettlementState
 *
 * 上一日结算状态（只读快照）：用于 UI 展示经济/生产/人口等具有固定结算周期（按“日”）的数据。
 */
public class DailySettlementState {

    /**
     * 该快照对应的“已落账日序号”（上一日）。
     */
    public int settledDay;

    /**
     * 占位：星区总数（用于验证快照链路）。
     */
    public int sectorCount;

    public DailySettlementState() {
    }

    public void resetForFill() {
        settledDay = 0;
        sectorCount = 0;
    }
}
