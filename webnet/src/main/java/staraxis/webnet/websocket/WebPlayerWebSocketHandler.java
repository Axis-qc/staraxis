package staraxis.webnet.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import staraxis.game.log.GameLog;
import staraxis.game.StarAxisGameRuntime;
import staraxis.webnet.ai.WebAiAutoStarter;
import staraxis.webnet.auth.AuthStore;
import staraxis.webnet.command.WebCommandRegistry;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.dto.SnapshotHighFreqMessageDto;
import staraxis.webnet.dto.SnapshotLowFreqMessageDto;
import staraxis.webnet.dto.SnapshotMessageDto;
import staraxis.webnet.game.GameSessions;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * WebPlayerWebSocketHandler（玩家 WS 处理器）喵。
 *
 * 作用喵：
 * - 承载 /ws 端点的握手鉴权、消息分发与连接生命周期管理喵。
 * - 将原本内联在 WebNetServer 中的 WS 逻辑下沉，避免入口类承担具体实现喵。
 */
public class WebPlayerWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AuthStore authStore;
    private final WsConnectionManager connMgr;
    private final WebCommandRegistry commandRegistry;
    private final SnapshotBroadcaster snapshotBroadcaster;

    public WebPlayerWebSocketHandler(ObjectMapper objectMapper, AuthStore authStore, WsConnectionManager connMgr,
            WebCommandRegistry commandRegistry, SnapshotBroadcaster snapshotBroadcaster) {
        this.objectMapper = objectMapper;
        this.authStore = authStore;
        this.connMgr = connMgr;
        this.commandRegistry = commandRegistry;
        this.snapshotBroadcaster = snapshotBroadcaster;
    }

    /**
     * Undertow websocket onConnect 回调喵。
     */
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        List<String> tokenParams = exchange.getRequestParameters().get("token");
        String token = (tokenParams == null || tokenParams.isEmpty()) ? null : tokenParams.get(0);

        AuthStore.Session session = (token == null) ? null : authStore.getSessionByToken(token);
        if (session == null) {
            try {
                WebSockets.sendText(objectMapper.writeValueAsString(Map.of(
                        "type", "hello",
                        "ok", false,
                        "error", "unauthorized")), channel, null);
                channel.sendClose();
            } catch (Exception ignored) {
            }
            return;
        }

        String playerId = session.playerId;
        channel.setIdleTimeout(60_000L);
        String connectionId = connMgr.registerPlayer(playerId, channel);

        channel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                String text = message.getData();
                WebAiAutoStarter.reportActivity();

                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = objectMapper.readValue(text, Map.class);
                    Object typeObj = m.get("type");
                    String type = typeObj == null ? null : String.valueOf(typeObj);

                    if ("subscribeSnapshot".equals(type)) {
                        connMgr.subscribeSnapshot(channel);
                        sendSnapshotToChannel(channel);
                        return;
                    }

                    if ("unsubscribeSnapshot".equals(type)) {
                        connMgr.unsubscribeSnapshot(channel);
                        WebSockets.sendText("{\"type\":\"unsubscribed\",\"ok\":true}", channel, null);
                        return;
                    }

                    if ("requestFullSync".equals(type)) {
                        sendSnapshotToChannel(channel);
                        return;
                    }

                    if ("updateInterestEntities".equals(type)) {
                        connMgr.updateSnapshotInterestEntityIds(
                                channel,
                                parseInterestEntityIds(m.get("entityIds")));
                        WebSockets.sendText("{\"type\":\"interestEntitiesUpdated\",\"ok\":true}", channel, null);
                        return;
                    }

                    if ("startSnapshotTickTrace".equals(type)) {
                        long durationMs = parseDurationMs(m.get("durationMs"));
                        long traceUntilMs = connMgr.startSnapshotTickTrace(durationMs);
                        String connectionId = connMgr.getConnectionIdByChannel(channel);
                        GameLog.log(
                                "SnapshotTraceStart"
                                        + " player=" + playerId
                                        + " connectionId=" + connectionId
                                        + " durationMs=" + durationMs
                                        + " traceUntilRealMs=" + traceUntilMs);
                        WebSockets.sendText(
                                "{\"type\":\"snapshotTickTraceStarted\",\"ok\":true,\"durationMs\":" + durationMs
                                        + ",\"traceUntilRealMs\":" + traceUntilMs + "}",
                                channel, null);
                        return;
                    }

                    if ("toggleBroadcastTimingTrace".equals(type)) {
                        boolean state = snapshotBroadcaster.toggleBroadcastTimingTrace();
                        GameLog.log("BroadcastTimingTrace toggled by player=" + playerId + " state=" + state);
                        WebSockets.sendText(
                                "{\"type\":\"broadcastTimingTraceToggled\",\"ok\":true,\"active\":" + state + "}",
                                channel, null);
                        return;
                    }

                    if ("pong".equals(type)) {
                        connMgr.onPlayerPong(channel);
                        return;
                    }

                    if ("updateVisibleSectors".equals(type)) {
                        // 忽略前端上报的可见星区，由服务端情报系统权威计算喵
                        return;
                    }

                    if ("setNationId".equals(type)) {
                        Object nationIdObj = m.get("nationId");
                        String nationId = nationIdObj == null ? null : String.valueOf(nationIdObj).trim();
                        if (nationId != null && !nationId.isEmpty()) {
                            connMgr.setPlayerNationId(playerId, nationId);
                            WebSockets.sendText(
                                    "{\"type\":\"nationIdSet\",\"ok\":true,\"nationId\":\"" + nationId
                                            + "\"}",
                                    channel, null);
                        } else {
                            WebSockets.sendText(
                                    "{\"type\":\"nationIdSet\",\"ok\":false,\"error\":\"invalid_nation_id\"}",
                                    channel, null);
                        }
                        return;
                    }

                    if (commandRegistry.supports(type)) {
                        String response = commandRegistry.handleTextMessage(text);
                        WebSockets.sendText(response, channel, null);
                        return;
                    }
                } catch (Exception ignored) {
                }

                WebSockets.sendText(text, channel, null);
            }

            @Override
            protected void onClose(WebSocketChannel webSocketChannel,
                    io.undertow.websockets.core.StreamSourceFrameChannel frameChannel) {
                connMgr.unregisterPlayer(webSocketChannel);
            }
        });

        channel.resumeReceives();
        WebSockets.sendText(
                "{\"type\":\"hello\",\"ok\":true,\"server\":\"webnet\",\"playerId\":\"" + playerId
                        + "\",\"connectionId\":\"" + connectionId + "\"}",
                channel,
                null);
    }

    private void sendSnapshotToChannel(WebSocketChannel channel) {
        if (channel == null || !channel.isOpen()) {
            return;
        }

        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            try {
                WebSockets.sendText(
                        objectMapper.writeValueAsString(SnapshotHighFreqMessageDto.forError("world_not_created")),
                        channel, null);
                WebSockets.sendText(
                        objectMapper.writeValueAsString(SnapshotLowFreqMessageDto.forError("world_not_created")),
                        channel, null);
                WebSockets.sendText(
                        objectMapper.writeValueAsString(SnapshotMessageFactory.buildWorldNotCreatedMessage()),
                        channel, null);
            } catch (Exception e) {
                WebSockets.sendText("{\"type\":\"snapshot\",\"ok\":false,\"error\":\"world_not_created\"}",
                        channel, null);
            }
            return;
        }

        try {
            runtime.publishRealtimeSnapshotIfNeeded();
            String nationId = null;
            try {
                String pid = connMgr.getPlayerIdByChannel(channel);
                if (pid != null) {
                    nationId = runtime.getWorldStateForSimOnly().nationManager.getNationIdByPlayer(pid);
                }
            } catch (Exception ignored) {
            }
            if (nationId == null) {
                nationId = connMgr.getNationIdByChannel(channel);
            }

            // 可见星系由服务端权威计算（3D Octree 版本）
            var sysIntel = runtime.getWorldStateForSimOnly().intelSystem;
            Set<Long> visible;
            if (sysIntel != null) {
                visible = sysIntel.getVisibleEntities3D(nationId, 0);
            } else {
                visible = runtime.getWorldStateForSimOnly().visibilitySystem
                        .computeIntelVisibleSystems3D(nationId);
            }

            SnapshotMessageDto snapshotDto = SnapshotMessageFactory.buildSnapshotMessageWithNation(runtime, 0, visible,
                    nationId);
            SnapshotHighFreqMessageDto highFreqDto = SnapshotMessageFactory.buildHighFreqSnapshotMessage(snapshotDto);
            // 首次全量同步，低频携带全量实体基线喵
            SnapshotLowFreqMessageDto lowFreqDto = SnapshotMessageFactory.buildLowFreqSnapshotMessage(snapshotDto,
                    true);

            WebSockets.sendText(objectMapper.writeValueAsString(highFreqDto), channel, null);
            WebSockets.sendText(objectMapper.writeValueAsString(lowFreqDto), channel, null);
        } catch (Exception e) {
            WebSockets.sendText("{\"type\":\"snapshot\",\"ok\":false,\"error\":\"snapshot_build_failed\"}",
                    channel, null);
        }
    }

    /**
     * 解析前端上报的兴趣实体ID集合喵。
     */
    private Set<Long> parseInterestEntityIds(Object rawEntityIds) {
        Set<Long> entityIds = new HashSet<>();
        if (!(rawEntityIds instanceof List<?> rawList)) {
            return entityIds;
        }

        for (Object rawId : rawList) {
            if (rawId instanceof Number number) {
                entityIds.add(number.longValue());
                continue;
            }
            if (rawId instanceof String text) {
                try {
                    entityIds.add(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return entityIds;
    }

    /**
     * 解析前端上报的录制时长喵。
     */
    private long parseDurationMs(Object rawDurationMs) {
        if (rawDurationMs instanceof Number number) {
            return Math.max(1000L, Math.min(30_000L, number.longValue()));
        }
        if (rawDurationMs instanceof String text) {
            try {
                return Math.max(1000L, Math.min(30_000L, Long.parseLong(text)));
            } catch (NumberFormatException ignored) {
            }
        }
        return 10_000L;
    }
}
