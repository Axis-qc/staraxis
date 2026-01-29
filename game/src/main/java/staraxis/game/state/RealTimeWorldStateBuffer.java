package staraxis.game.state;

/**
 * RealTimeWorldStateBuffer
 *
 * RealTimeWorldState 的双缓冲容器。
 */
public class RealTimeWorldStateBuffer {

    private final RealTimeWorldState a = new RealTimeWorldState();

    private final RealTimeWorldState b = new RealTimeWorldState();

    private RealTimeWorldState active = a;

    private RealTimeWorldState inactive = b;

    public RealTimeWorldStateBuffer() {
    }

    /**
     * 供模拟层写入：返回 inactive 缓冲，并在返回前 reset。
     */
    public RealTimeWorldState beginFillInactive() {
        inactive.resetForFill();
        return inactive;
    }

    /**
     * 发布：交换 active/inactive。
     */
    public void swapPublish() {
        RealTimeWorldState tmp = active;
        active = inactive;
        inactive = tmp;
    }

    /**
     * 对外只读：获取 active 快照。
     */
    public RealTimeWorldState getActive() {
        return active;
    }
}
