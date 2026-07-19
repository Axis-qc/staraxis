package staraxis;

import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;

/**
 * GameSnapshotProvider（游戏快照提供者接口）。
 *
 * 抽象 client 模块读取游戏快照的途径：
 * - 本地主机：从当前进程的 game runtime 内存直接读取
 * - 远程客机：从 WebSocket 收到的快照缓存中读取
 *
 * 所有绘制/交互代码通过此接口获取数据，不关心数据来源。
 * 写入逻辑仍通过 StarAxisGameRuntime.submitCommand() 走命令总线。
 */
public interface GameSnapshotProvider {

    /** 获取最近一帧的实时快照（含 SHIP 动态实体 + 时间元信息）。 */
    RealTimeWorldState getRealtimeState();

    /** 获取最近的每日基线快照（含 STAR/PLANET/ASTEROID/MOON 天体基线）。 */
    DailySettlementState getDailyState();
}
