package staraxis.game.state;

/**
 * DailySettlementStateBuffer
 *
 * DailySettlementState 的发布容器：每次跨日结算后生成/更新并发布。
 * 采用简单的替换机制，因为日结算频率较低（每游戏日一次），暂不强制要求 RealTimeWorldState 那样的无锁双缓冲喵。
 */
public class DailySettlementStateBuffer {

    private DailySettlementState active = new DailySettlementState();

    public DailySettlementStateBuffer() {
    }

    public synchronized DailySettlementState getActive() {
        return active;
    }

    /**
     * 模拟层：在跨日结算时发布新的状态喵。
     */
    public synchronized void publish(DailySettlementState newState) {
        this.active = newState;
    }

    /**
     * 模拟层：简单更新 active（向下兼容）喵。
     */
    public synchronized void publishForDay(int settledDay, int sectorCount) {
        DailySettlementState next = new DailySettlementState();
        next.settledDay = settledDay;
        next.sectorCount = sectorCount;
        this.active = next;
    }
}
