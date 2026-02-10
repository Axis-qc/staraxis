package staraxis.webnet.ai;

import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.webnet.auth.AuthStore;
import staraxis.webnet.core.GameLog;
import staraxis.webnet.game.GameSessions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebAiWebSocketHandler
 *
 * 作用（description）：
 * - 为外部 AI/Agent 提供专用的 WebSocket 接入点（/ws/ai）。
 * - 基于 Authorization: Bearer <token> 复用现有 AuthStore 会话体系进行鉴权。
 * - 负责解析基础协议（如 hello），后续通过工具调用（tool.call）与游戏世界交互。
 *
 * 提供的接口/API：
 * - onConnect(exchange, channel)：在 WebNetServer 中作为 WebSocket 入口被调用，完成鉴权与基础握手。
 * - 处理消息类型：
 * - {"type":"ai.hello"}：AI 会话握手，返回当前会话概要与可用能力占位字段。
 *
 * 使用方式（usage）：
 * - 由 WebNetServer 在 routes.addPrefixPath("/ws/ai", ...) 中创建实例并委托处理。
 * - 外部 AI 客户端在建立 WS 连接时附带 HTTP Header: Authorization: Bearer <token>。
 *
 * 注意事项（important_notes）：
 * - 当前实现仅提供最小可用握手能力，不暴露任何读取/写入游戏状态的接口。
 * - 所有后续涉及游戏快照读取、命令发送的能力必须通过白名单工具调用（tool.call）扩展，并在此处集中校验权限。
 * - 本 handler 不负责 tick 驱动与广播，仅处理 AI 专用的点对点消息。
 */
public class WebAiWebSocketHandler extends AbstractReceiveListener {

    private final AuthStore authStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebAiWebSocketHandler(AuthStore authStore) {
        this.authStore = authStore;
    }

    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        try {
            String auth = exchange.getRequestHeaders().get("Authorization") != null
                    && !exchange.getRequestHeaders().get("Authorization").isEmpty()
                            ? exchange.getRequestHeaders().get("Authorization").get(0)
                            : null;
            AuthStore.Session session = authStore.getSessionFromAuthorizationHeader(auth);
            if (session == null) {
                WebSockets.sendText("{\"type\":\"ai.hello\",\"ok\":false,\"error\":\"unauthorized\"}", channel, null);
                try {
                    channel.close();
                } catch (Exception ignored) {
                }
                return;
            }

            AuthStore.Account a = authStore.loadAccount(session.username);
            String role = a != null && a.role != null && !a.role.isBlank() ? a.role : "USER";
            String capabilities = "[\"snapshot.getEntity\", \"snapshot.getLatestSummary\"]";

            GameLog.log("AI WS Handshake [SUCCESS]: user=" + session.username + " role=" + role + " capabilities="
                    + capabilities);

            channel.getReceiveSetter().set(this);
            channel.resumeReceives();

            String hello = "{" +
                    "\"type\":\"ai.hello\"," +
                    "\"ok\":true," +
                    "\"username\":\"" + session.username + "\"," +
                    "\"playerId\":\"" + session.playerId + "\"," +
                    "\"role\":\"" + role + "\"," +
                    "\"capabilities\":" + capabilities +
                    "}";
            WebSockets.sendText(hello, channel, null);
        } catch (Exception e) {
            GameLog.log("AI WS connect failed: " + e.getMessage());
            try {
                WebSockets.sendText("{\"type\":\"ai.hello\",\"ok\":false,\"error\":\"internal_error\"}", channel, null);
            } catch (Exception ignored) {
            }
            try {
                channel.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
        String text = message.getData();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(text, Map.class);
            String type = (String) msg.get("type");

            if ("ai.tool.call".equals(type)) {
                handleToolCall(channel, msg);
            } else {
                // 回显其他消息
                WebSockets.sendText(text, channel, null);
            }
        } catch (Exception e) {
            try {
                Map<String, Object> err = new HashMap<>();
                err.put("type", "ai.error");
                err.put("ok", false);
                err.put("error", e.getMessage());
                WebSockets.sendText(objectMapper.writeValueAsString(err), channel, null);
            } catch (Exception ignored) {
            }
        }
    }

    private void handleToolCall(WebSocketChannel channel, Map<String, Object> msg) throws Exception {
        // AI 主动调用工具也算活动，用于续命自动退出计时喵
        WebAiAutoStarter.reportActivity();
        String tool = (String) msg.get("tool");
        String requestId = (String) msg.get("requestId");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) msg.get("args");

        Map<String, Object> response = new HashMap<>();
        response.put("type", "ai.tool.result");
        response.put("requestId", requestId);
        response.put("tool", tool);

        if ("snapshot.getEntity".equals(tool)) {
            if (args == null || !args.containsKey("entityId")) {
                response.put("ok", false);
                response.put("error", "missing entityId");
            } else {
                long entityId = Long.parseLong(String.valueOf(args.get("entityId")));
                StarAxisGameRuntime runtime = GameSessions.getRuntime();

                if (runtime == null) {
                    response.put("ok", false);
                    response.put("error", "game_not_running");
                } else {
                    List<EntitySnapshot> snapshots = runtime.getRealTimeWorldStateReadonly()
                            .getEntitySnapshotsView();

                    EntitySnapshot target = null;
                    for (EntitySnapshot s : snapshots) {
                        if (s.entityId == entityId) {
                            target = s;
                            break;
                        }
                    }

                    if (target == null) {
                        response.put("ok", false);
                        response.put("error", "entity_not_found");
                    } else {
                        response.put("ok", true);
                        response.put("result", target);
                    }
                }
            }
        } else if ("snapshot.getLatestSummary".equals(tool)) {
            StarAxisGameRuntime runtime = GameSessions.getRuntime();
            if (runtime == null) {
                response.put("ok", false);
                response.put("error", "game_not_running");
            } else {
                response.put("ok", true);
                response.put("result", staraxis.webnet.websocket.SnapshotMessageFactory.buildWorldSummary(runtime));
            }
        } else {
            response.put("ok", false);
            response.put("error", "unsupported_tool");
        }

        WebSockets.sendText(objectMapper.writeValueAsString(response), channel, null);
    }
}
