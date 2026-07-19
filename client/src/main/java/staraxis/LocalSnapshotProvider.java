package staraxis;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;

/**
 * LocalSnapshotProvider（本地快照提供者）。
 *
 * 主机模式下使用，直接从当前进程的 StarAxisGameRuntime 内存中读取双缓冲快照。
 * 单人模式和多人主机端使用此实现。
 */
public class LocalSnapshotProvider implements GameSnapshotProvider {

    private final StarAxisGameRuntime runtime;

    public LocalSnapshotProvider(StarAxisGameRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public RealTimeWorldState getRealtimeState() {
        return runtime.getRealTimeWorldStateReadonly();
    }

    @Override
    public DailySettlementState getDailyState() {
        return runtime.getDailySettlementStateBufferForReadonly().getActive();
    }
}
