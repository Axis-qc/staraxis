package staraxis.webnet.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.world.hex.SectorCoord;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.game.GameSessions;
import staraxis.webnet.api.joingame.WorldSavesApi;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SnapshotBroadcaster（快照广播器）喵。
 *
 * 作用喵：
 * - 承载原本内联在 WebNetServer.tickAndBroadcastSnapshots 的具体实现喵。
 * - WebNetServer 仅负责定时调度调用本类，不负责具体广播逻辑喵。
 */
public class SnapshotBroadcaster {

    /** 自动存档间隔（tick）：300 tick 约 5 分钟（按 1Hz tick）喵。 */
    private static final long AUTO_SAVE_INTERVAL_TICKS = 300L;

    private final ObjectMapper objectMapper;
    private final WsConnectionManager connMgr;
    private final AtomicLong lastTickCostMs;
    private final Map<String, Long> lastAutoSaveTickByWorldId = new java.util.concurrent.ConcurrentHashMap<>();

    public SnapshotBroadcaster(ObjectMapper objectMapper, WsConnectionManager connMgr, AtomicLong lastTickCostMs) {
        this.objectMapper = objectMapper;
        this.connMgr = connMgr;
        this.lastTickCostMs = lastTickCostMs;
    }

    /**
     * 执行一次 tick 并向订阅者广播快照喵。
     */
    public void tickAndBroadcast() {
        String activeWorldId = GameSessions.getActiveWorldId();
        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            return;
        }

        // 世界推进策略（tickPolicy）喵：
        // - ALWAYS_RUN：始终推进权威时间轴喵。
        // - RUN_WHEN_ONLINE：仅当有玩家加入该世界时推进（单机/存档友好）喵。
        String tickPolicy = GameSessions.getActiveTickPolicy();
        boolean shouldAdvance = true;
        if ("RUN_WHEN_ONLINE".equals(tickPolicy)) {
            int joinedPlayers = GameSessions.getJoinedPlayerCount(activeWorldId);
            shouldAdvance = joinedPlayers > 0;
        }

        if (shouldAdvance) {
            long t0 = System.nanoTime();
            try {
                runtime.update(0f);
            } catch (Exception e) {
                return;
            } finally {
                long costMs = Math.max(0, (System.nanoTime() - t0) / 1_000_000L);
                lastTickCostMs.set(costMs);
            }

            // 自动存档：按 tick 间隔触发，最多保留 4 个 autosave 文件喵。
            long nowTick = runtime.getRealTimeWorldStateReadonly().simulationTick;
            long lastTick = lastAutoSaveTickByWorldId.getOrDefault(activeWorldId, 0L);
            if (nowTick - lastTick >= AUTO_SAVE_INTERVAL_TICKS) {
                boolean saved = WorldSavesApi.tryAutoSave(objectMapper, activeWorldId);
                if (saved) {
                    lastAutoSaveTickByWorldId.put(activeWorldId, nowTick);
                }
            }
        } else {
            // 暂停推进时保持 tickCost 为 0，便于前端识别“已暂停推进”状态喵。
            lastTickCostMs.set(0);
        }

        try {
            Set<WebSocketChannel> snapshotSubscribers = connMgr.getSnapshotSubscribers();
            if (snapshotSubscribers.isEmpty()) {
                return;
            }

            for (WebSocketChannel ch : snapshotSubscribers) {
                if (ch != null && ch.isOpen()) {
                    String nationId = null;
                    try {
                        String pid = connMgr.getPlayerIdByChannel(ch);
                        if (pid != null) {
                            nationId = runtime.getWorldStateForSimOnly().nationManager.getNationIdByPlayer(pid);
                        }
                    } catch (Exception ignored) {
                    }
                    if (nationId == null) {
                        nationId = connMgr.getNationIdByChannel(ch);
                    }

                    // 可见星区由服务端权威计算：本国拥有实体所在星区 + 周边一圈喵
                    Set<SectorCoord> visible = runtime.getWorldStateForSimOnly().visibilitySystem
                            .computeIntelVisibleSectorsForNation(nationId);

                    // 记录快照生成时间喵
                    long snapshotStartTime = System.nanoTime();
                    var snapshotDto = SnapshotMessageFactory.buildSnapshotMessageWithNation(runtime,
                            lastTickCostMs.get(),
                            visible, nationId);
                    String json = objectMapper.writeValueAsString(snapshotDto);
                    long snapshotBuildTimeMs = (System.nanoTime() - snapshotStartTime) / 1_000_000L;

                    // 更新性能监测器中的快照生成时间喵
                    staraxis.game.log.PerformanceMonitor.getInstance().updateLastSnapshotBuildTime(snapshotBuildTimeMs);

                    WebSockets.sendText(json, ch, null);
                }
            }
        } catch (Exception e) {
            try {
                staraxis.webnet.core.WebNetLog
                        .log("SnapshotBroadcaster tickAndBroadcast snapshot_build_failed: " + String.valueOf(e));
            } catch (Exception ignored) {
            }
        }
    }
}
