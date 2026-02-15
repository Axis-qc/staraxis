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
 * - 使用玩家 token 进行认证，AI 数据访问权限与玩家账户绑定。
 * - 工具调用时根据玩家权限返回数据（如视野范围、已探索区域等）。
 *
 * 提供的接口/API：
 * - onConnect(exchange, channel)：完成鉴权与基础握手，将玩家 session 附加到 channel。
 * - handleToolCall(channel, msg)：处理工具调用，使用玩家权限过滤数据。
 *
 * 权限控制：
 * - AI 使用玩家 token 连接，只能访问该玩家有权限查看的数据
 * - snapshot.getEntity: 只能获取玩家视野内的实体
 * - snapshot.getLatestSummary: 返回玩家已知的世界概览
 *
 * 注意事项（important_notes）：
 * - 工具调用时从 channel 获取玩家 session，确保权限隔离
 * - 实体查询需检查玩家视野/情报权限
 */
public class WebAiWebSocketHandler extends AbstractReceiveListener {

    private final AuthStore authStore;
    private final staraxis.webnet.core.WsConnectionManager connMgr;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebAiWebSocketHandler(AuthStore authStore, staraxis.webnet.core.WsConnectionManager connMgr) {
        this.authStore = authStore;
        this.connMgr = connMgr;
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

            // 在 WsConnectionManager 中注册并绑定 playerId，同时生成 connectionId 喵
            String connectionId = null;
            if (connMgr != null) {
                connectionId = connMgr.registerAiForPlayer(session.playerId, channel);
            }

            AuthStore.Account a = authStore.loadAccount(session.username);
            String role = a != null && a.role != null && !a.role.isBlank() ? a.role : "USER";
            String capabilities = "[\"snapshot.getEntity\", \"snapshot.getLatestSummary\"]";

            // 将玩家 session 附加到 channel，供后续工具调用使用
            channel.setAttribute("playerSession", session);

            staraxis.webnet.core.WebNetLog.log("AI WS Handshake [SUCCESS]: user=" + session.username + " playerId="
                    + session.playerId + " role=" + role);

            channel.getReceiveSetter().set(this);
            channel.resumeReceives();

            String hello = "{" +
                    "\"type\":\"ai.hello\"," +
                    "\"ok\":true," +
                    "\"username\":\"" + session.username + "\"," +
                    "\"playerId\":\"" + session.playerId + "\"," +
                    "\"connectionId\":\"" + (connectionId == null ? "" : connectionId) + "\"," +
                    "\"role\":\"" + role + "\"," +
                    "\"capabilities\":" + capabilities +
                    "}";
            WebSockets.sendText(hello, channel, null);
        } catch (Exception e) {
            staraxis.webnet.core.WebNetLog.log("AI WS connect failed: " + e.getMessage());
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
        WebAiAutoStarter.reportActivity();
        String tool = (String) msg.get("tool");
        String requestId = (String) msg.get("requestId");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) msg.get("args");

        // 获取玩家 session
        AuthStore.Session session = (AuthStore.Session) channel.getAttribute("playerSession");
        if (session == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "ai.tool.result");
            response.put("requestId", requestId);
            response.put("tool", tool);
            response.put("ok", false);
            response.put("error", "player session not found");
            WebSockets.sendText(objectMapper.writeValueAsString(response), channel, null);
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", "ai.tool.result");
        response.put("requestId", requestId);
        response.put("tool", tool);

        if ("snapshot.getEntity".equals(tool)) {
            handleGetEntity(args, session, response);
        } else if ("snapshot.getLatestSummary".equals(tool)) {
            handleGetLatestSummary(session, response);
        } else {
            response.put("ok", false);
            response.put("error", "unsupported_tool");
        }

        WebSockets.sendText(objectMapper.writeValueAsString(response), channel, null);
    }

    private void handleGetEntity(Map<String, Object> args, AuthStore.Session session, Map<String, Object> response) {
        if (args == null || !args.containsKey("entityId")) {
            response.put("ok", false);
            response.put("error", "missing entityId");
            return;
        }

        long entityId = Long.parseLong(String.valueOf(args.get("entityId")));
        StarAxisGameRuntime runtime = GameSessions.getRuntime();

        if (runtime == null) {
            response.put("ok", false);
            response.put("error", "game_not_running");
            return;
        }

        // TODO: 实现玩家权限检查
        // 1. 获取玩家视野范围
        // 2. 检查 entityId 是否在玩家视野内
        // 3. 如果不在视野内，返回权限错误

        // 临时实现：允许访问（后续需要接入实际的视野系统）
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
            // 权限检查：这里应该检查玩家是否有权限查看该实体
            // 比如：是否是自己的舰船、是否在传感器范围内、是否是友方单位等
            boolean hasPermission = checkEntityPermission(target, session);

            if (!hasPermission) {
                response.put("ok", false);
                response.put("error", "no_permission");
                response.put("message", "该实体不在您的视野范围内");
            } else {
                response.put("ok", true);
                response.put("result", target);
            }
        }
    }

    private void handleGetLatestSummary(AuthStore.Session session, Map<String, Object> response) {
        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            response.put("ok", false);
            response.put("error", "game_not_running");
            return;
        }

        // TODO: 根据玩家权限过滤世界概要
        // 1. 只返回玩家已探索/已知的区域信息
        // 2. 只返回玩家有权限查看的势力信息
        // 3. 隐藏敌方未暴露的情报

        // 临时实现：返回完整概览（后续需要接入实际的视野系统）
        response.put("ok", true);
        response.put("result", staraxis.webnet.websocket.SnapshotMessageFactory.buildWorldSummary(runtime));
        response.put("note", "当前返回完整概览，后续将根据玩家视野过滤");
    }

    /**
     * 检查玩家是否有权限查看实体
     * 
     * TODO: 接入实际的游戏视野/情报系统
     * 目前简单允许所有访问，后续需要实现：
     * 1. 检查实体是否属于玩家
     * 2. 检查实体是否在玩家传感器范围内
     * 3. 检查是否是友方单位
     * 4. 检查玩家是否有情报网覆盖
     */
    private boolean checkEntityPermission(EntitySnapshot entity, AuthStore.Session session) {
        // 临时：允许所有访问
        // 实际实现应该根据游戏逻辑判断
        return true;
    }
}
