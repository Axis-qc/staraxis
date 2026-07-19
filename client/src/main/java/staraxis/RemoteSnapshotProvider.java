package staraxis;

import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;

/**
 * RemoteSnapshotProvider（远程快照提供者）。
 *
 * 客机模式下使用，从 WebSocket 收到的快照缓存中读取。
 *
 * TODO 多人联机：此为桩代码，待 WebSocket 客户端实现后填充。
 * 需在 WebSocket 消息处理器中将 JSON 反序列化为 RealTimeWorldState
 * 和 DailySettlementState，存入本地环形缓存，由此提供者返回。
 */
public class RemoteSnapshotProvider implements GameSnapshotProvider {

    // TODO 多人联机：存储 WebSocket 接收到的快照缓存
    // private volatile RealTimeWorldState cachedRealtime;
    // private volatile DailySettlementState cachedDaily;

    @Override
    public RealTimeWorldState getRealtimeState() {
        // TODO 返回从 WebSocket 缓存的反序列化快照
        return null;
    }

    @Override
    public DailySettlementState getDailyState() {
        // TODO 返回从 WebSocket 缓存的反序列化快照
        return null;
    }
}
