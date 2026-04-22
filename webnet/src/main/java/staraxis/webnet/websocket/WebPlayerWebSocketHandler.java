package staraxis.webnet.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.intel.IntelSystem;
import staraxis.game.nation.VisibilitySystem;
import staraxis.game.world.hex.SectorCoord;
import staraxis.webnet.ai.WebAiAutoStarter;
import staraxis.webnet.auth.AuthStore;
import staraxis.webnet.command.WebCommandRegistry;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.game.GameSessions;

import java.util.List;
import java.util.Map;
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

    public WebPlayerWebSocketHandler(ObjectMapper objectMapper, AuthStore authStore, WsConnectionManager connMgr,
            WebCommandRegistry commandRegistry) {
        this.objectMapper = objectMapper;
        this.authStore = authStore;
        this.connMgr = connMgr;
        this.commandRegistry = commandRegistry;
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
                        if (!GameSessions.hasRuntime()) {
                            WebSockets.sendText(
                                    "{\"type\":\"snapshot\",\"ok\":false,\"error\":\"world_not_created\"}",
                                    channel,
                                    null);
                        } else {
                            sendSnapshotToChannel(channel);
                        }
                        return;
                    }

                    if ("unsubscribeSnapshot".equals(type)) {
                        connMgr.unsubscribeSnapshot(channel);
                        WebSockets.sendText("{\"type\":\"unsubscribed\",\"ok\":true}", channel, null);
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

            // 可见星区由服务端权威计算：基于情报系统的完整探测等级喵
            IntelSystem intelSystem = runtime.getWorldStateForSimOnly().intelSystem;
            Set<SectorCoord> visible;
            if (intelSystem != null) {
                visible = intelSystem.computeIntelVisibleSectors(nationId);
            } else {
                // 情报系统未初始化，回退到简化可见性计算喵
                System.err.println("[WebPlayerWebSocketHandler] IntelSystem not initialized, falling back to visibility system喵");
                visible = runtime.getWorldStateForSimOnly().visibilitySystem
                        .computeIntelVisibleSectorsForNation(nationId);
            }

            String json = objectMapper.writeValueAsString(
                    SnapshotMessageFactory.buildSnapshotMessageWithNation(runtime, 0, visible, nationId));
            WebSockets.sendText(json, channel, null);
        } catch (Exception e) {
            WebSockets.sendText("{\"type\":\"snapshot\",\"ok\":false,\"error\":\"snapshot_build_failed\"}",
                    channel, null);
        }
    }
}
