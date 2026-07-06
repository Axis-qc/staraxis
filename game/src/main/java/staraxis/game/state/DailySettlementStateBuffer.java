package staraxis.game.state;

/**
 * DailySettlementStateBuffer
 *
 * DailySettlementState 的双缓冲发布容器。
 * - gameTicker 线程通过 beginFillInactive() + swapPublish() 写入。
 * - 外部通过 getActive() 读取（volatile 可见性，无锁）。
 * - 低频快照体量较大（恒星基线 + 可见性预计算），不适合每 tick swap，
 *   改造为与 RealTimeWorldStateBuffer 一致的双缓冲模式。
 */
public class DailySettlementStateBuffer {

    private final DailySettlementState a = new DailySettlementState();
    private final DailySettlementState b = new DailySettlementState();

    private volatile DailySettlementState active = a;
    private DailySettlementState inactive = b;

    public DailySettlementStateBuffer() {
    }

    /**
     * 获取当前可读的活跃快照（volatile 无锁读取）。
     */
    public DailySettlementState getActive() {
        return active;
    }

    /**
     * 模拟层：返回 inactive 缓冲用于填充写入，写入前自动 reset。
     */
    public DailySettlementState beginFillInactive() {
        inactive.resetForFill();
        return inactive;
    }

    /**
     * 模拟层：填充完成后原子交换 active/inactive。
     * 写入 inactive → volatile 写 active → 外部读取立即看到新数据。
     */
    public void swapPublish() {
        DailySettlementState tmp = active;
        active = inactive;
        inactive = tmp;
    }

    /**
     * 模拟层：兼容旧接口，直接替换 active（向下兼容，推荐改用双缓冲模式）。
     */
    public synchronized void publish(DailySettlementState newState) {
        this.active = newState;
    }

    /**
     * 模拟层：兼容旧接口，创建新实例后替换 active（向下兼容）。
     */
    public synchronized void publishForDay(int settledDay, int sectorCount) {
        DailySettlementState next = new DailySettlementState();
        next.settledDay = settledDay;
        next.sectorCount = sectorCount;
        this.active = next;
    }
}
