package staraxis.game.sim;

import staraxis.game.space.event.CrossSystemEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * WorkerLocalBuffer（Worker 本地缓冲区）。
 *
 * 每个 Worker 线程持有自己的缓冲区（不共享，无需锁），
 * 用于暂存本 tick 新产生的跨系统事件和星系负载快照。
 *
 * 阶段4 由主线程遍历所有 WorkerLocalBuffer，合并到全局事件表。
 */
public class WorkerLocalBuffer {

    /** Worker 编号（0-based，与 LPT 分配中的 threadId 一致）。 */
    public final int workerId;

    /** 本 tick 新产生的跨系统事件列表（出发、中断等），阶段4 由主线程合并。 */
    public final List<CrossSystemEvent> pendingOut = new ArrayList<>();

    /** 本 worker 负责星系的最新负载快照（用于重平衡检测）。 */
    public long[] systemLoads;

    public WorkerLocalBuffer(int workerId) {
        this.workerId = workerId;
        this.systemLoads = new long[0];
    }

    /** 清空 pendingOut（每 tick 阶段4 合并后调用）。 */
    public void clear() {
        pendingOut.clear();
    }
}
