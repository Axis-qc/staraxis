package staraxis.game.state;

/**
 * DailySettlementStateBuffer
 *
 * DailySettlementState 的发布容器：每次跨日结算后生成/更新并发布。
 */
public class DailySettlementStateBuffer {

    private final DailySettlementState active = new DailySettlementState();

    public DailySettlementStateBuffer() {
    }

    public DailySettlementState getActive() {
        return active;
    }

    /**
     * 模拟层：在跨日结算时更新 active。
     */
    public void publishForDay(int settledDay, int sectorCount) {
        active.resetForFill();
        active.settledDay = settledDay;
        active.sectorCount = sectorCount;
    }
}
