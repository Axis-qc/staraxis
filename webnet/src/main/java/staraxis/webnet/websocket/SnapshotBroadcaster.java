package staraxis.webnet.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import staraxis.game.log.GameLog;
import staraxis.game.StarAxisGameRuntime;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.dto.SnapshotHighFreqMessageDto;
import staraxis.webnet.dto.SnapshotLowFreqMessageDto;
import staraxis.webnet.dto.SnapshotMessageDto;
import staraxis.webnet.game.GameSessions;
import staraxis.webnet.api.joingame.WorldSavesApi;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final Map<String, Boolean> hadSnapshotSubscriberByWorldId = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> lastLowFreqBroadcastAtMsByWorldId = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> lastLoggedHighFreqTickByWorldId = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Boolean> lastTraceActiveByWorldId = new java.util.concurrent.ConcurrentHashMap<>();

    /** 广播计时追踪开关：由前端调试窗口触发喵 */
    private volatile boolean broadcastTimingTraceActive = false;
    /** 低频快照异步序列化+发送线程池，避免阻塞 gameTicker 喵 */
    private final ExecutorService lowFreqExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webnet-lowfreq-sender");
        t.setDaemon(true);
        return t;
    });

    /**
     * 切换广播计时追踪模式喵。
     * @return 切换后的状态
     */
    public boolean toggleBroadcastTimingTrace() {
        broadcastTimingTraceActive = !broadcastTimingTraceActive;
        return broadcastTimingTraceActive;
    }

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
        Set<WebSocketChannel> snapshotSubscribers = connMgr.getSnapshotSubscribers();
        runtime.replaceFullRealtimeSimulationEntityIds(
                connMgr.getWorldUnionSnapshotInterestEntityIds());

        // 世界推进策略（tickPolicy）喵：
        // - ALWAYS_RUN：始终推进权威时间轴喵。
        // - RUN_WHEN_ONLINE：仅当有玩家加入该世界时推进（单机/存档友好）喵。
        String tickPolicy = GameSessions.getActiveTickPolicy();
        boolean shouldAdvance = true;
        if ("RUN_WHEN_ONLINE".equals(tickPolicy)) {
            boolean hasSubscribers = !snapshotSubscribers.isEmpty();
            if (hasSubscribers) {
                hadSnapshotSubscriberByWorldId.put(activeWorldId, true);
            }
            shouldAdvance = hasSubscribers;
            if (!shouldAdvance) {
                lastTickCostMs.set(0);
                if (Boolean.TRUE.equals(hadSnapshotSubscriberByWorldId.get(activeWorldId))) {
                    WorldSavesApi.tryAutoSave(objectMapper, activeWorldId);
                    GameSessions.unregisterRuntime(activeWorldId);
                }
                lastAutoSaveTickByWorldId.remove(activeWorldId);
                hadSnapshotSubscriberByWorldId.remove(activeWorldId);
                lastLowFreqBroadcastAtMsByWorldId.remove(activeWorldId);
                return;
            }
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
            if (snapshotSubscribers.isEmpty()) {
                return;
            }

            long nowMs = System.currentTimeMillis();
            // 当前阶段用于排查“后端是否偶发漏发 Tick”喵。
            // 只要存在快照订阅者喵，就强制把本 Tick 的权威状态发布到活动缓冲并广播喵。
            // 这样前端收到的高频快照就应当严格按 50ms 一个 Tick 连续到达喵。
            runtime.publishRealtimeSnapshotForced();

            // 低频快照时机判断：每秒一次喵
            final boolean shouldSendLowFreq = nowMs
                    - lastLowFreqBroadcastAtMsByWorldId.getOrDefault(activeWorldId, 0L) >= 1000L;
            if (shouldSendLowFreq) {
                lastLowFreqBroadcastAtMsByWorldId.put(activeWorldId, nowMs);
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

                    // 可见星系由服务端权威计算（3D Octree 版本）
                    Set<Long> visible = runtime.getWorldStateForSimOnly().visibilitySystem
                            .computeIntelVisibleSystems3D(nationId);

                    // ===== 分段计时：定位广播瓶颈喵 =====
                    long tDtoStart = System.nanoTime();
                    SnapshotMessageDto snapshotDto = SnapshotMessageFactory.buildSnapshotMessageWithNation(runtime,
                            lastTickCostMs.get(),
                            visible, nationId);
                    SnapshotHighFreqMessageDto highFreqDto = SnapshotMessageFactory
                            .buildHighFreqSnapshotMessage(snapshotDto);
                    // 主线程从已构建的全量DTO提取低频DTO（引用拷贝，微秒级）喵。
                    // 异步线程只做序列化+发送，不碰 runtime，避免双缓冲竞态喵。
                    SnapshotLowFreqMessageDto lowFreqDto = shouldSendLowFreq
                            ? SnapshotMessageFactory.buildLowFreqSnapshotMessage(snapshotDto, true)
                            : null;
                    long tDtoEnd = System.nanoTime();

                    String highFreqJson = objectMapper.writeValueAsString(highFreqDto);
                    long tSerEnd = System.nanoTime();

                    // 高频快照同步发送（轻量，含舰船）喵
                    WebSockets.sendText(highFreqJson, ch, null);
                    long tSendEnd = System.nanoTime();

                    // 低频快照：异步序列化+发送，不阻塞 gameTicker 喵
                    if (lowFreqDto != null) {
                        final SnapshotLowFreqMessageDto finalLowFreq = lowFreqDto;
                        final WebSocketChannel finalCh = ch;
                        lowFreqExecutor.submit(() -> {
                            try {
                                String json = objectMapper.writeValueAsString(finalLowFreq);
                                WebSockets.sendText(json, finalCh, null);
                            } catch (Exception ignored) {
                            }
                        });
                    }

                    long buildDtoMs = (tDtoEnd - tDtoStart) / 1_000_000L;
                    long serializeMs = (tSerEnd - tDtoEnd) / 1_000_000L;
                    long sendMs = (tSendEnd - tSerEnd) / 1_000_000L;

                    // 更新性能监测器中的快照生成时间（DTO构建+序列化）喵
                    staraxis.game.log.PerformanceMonitor.getInstance()
                            .updateLastSnapshotBuildTime(buildDtoMs + serializeMs);

                    if (broadcastTimingTraceActive) {
                        staraxis.webnet.core.WebNetLog.log(
                                "BroadcastTimer buildDto=" + buildDtoMs + "ms"
                                        + " serialize=" + serializeMs + "ms"
                                        + " send=" + sendMs + "ms"
                                        + " lowFreq=" + (lowFreqDto != null ? "async" : "skip")
                                        + " tick=" + runtime.getRealTimeWorldStateReadonly().simulationTick
                                        + " entities=" + (snapshotDto.realTimeWorldState != null
                                                ? snapshotDto.realTimeWorldState.entities.size() : 0));
                    }
                }
            }
            boolean traceActive = connMgr.isSnapshotTickTraceActive(nowMs);
            lastTraceActiveByWorldId.put(activeWorldId, traceActive);

            if (traceActive) {
                long sentTick = runtime.getRealTimeWorldStateReadonly().simulationTick;
                long previousLoggedTick = lastLoggedHighFreqTickByWorldId.getOrDefault(activeWorldId, sentTick);
                long tickGap = sentTick - previousLoggedTick;
                GameLog.log(
                        "SnapshotTx"
                                + " world=" + activeWorldId
                                + " tick=" + sentTick
                                + " tickGap="
                                + (lastLoggedHighFreqTickByWorldId.containsKey(activeWorldId) ? tickGap : 0)
                                + " txRealMs=" + nowMs
                                + " subscriberCount=" + snapshotSubscribers.size()
                                + " lowFreqQueued=" + shouldSendLowFreq);
                lastLoggedHighFreqTickByWorldId.put(activeWorldId, sentTick);
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
