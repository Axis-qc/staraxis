package staraxis.webnet.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.world.hex.SectorCoord;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.game.GameSessions;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SnapshotBroadcaster（快照广播器）喵。
 *
 * 作用喵：
 * - 承载原本内联在 WebNetServer.tickAndBroadcastSnapshots 的具体实现喵。
 * - WebNetServer 仅负责定时调度调用本类，不负责具体广播逻辑喵。
 */
public class SnapshotBroadcaster {

    private final ObjectMapper objectMapper;
    private final WsConnectionManager connMgr;
    private final AtomicLong lastTickCostMs;

    public SnapshotBroadcaster(ObjectMapper objectMapper, WsConnectionManager connMgr, AtomicLong lastTickCostMs) {
        this.objectMapper = objectMapper;
        this.connMgr = connMgr;
        this.lastTickCostMs = lastTickCostMs;
    }

    /**
     * 执行一次 tick 并向订阅者广播快照喵。
     */
    public void tickAndBroadcast() {
        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            return;
        }

        long t0 = System.nanoTime();
        try {
            runtime.update(0f);
        } catch (Exception e) {
            return;
        } finally {
            long costMs = Math.max(0, (System.nanoTime() - t0) / 1_000_000L);
            lastTickCostMs.set(costMs);
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

                    var snapshotDto = SnapshotMessageFactory.buildSnapshotMessageWithNation(runtime,
                            lastTickCostMs.get(),
                            visible, nationId);
                    String json = objectMapper.writeValueAsString(snapshotDto);
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
