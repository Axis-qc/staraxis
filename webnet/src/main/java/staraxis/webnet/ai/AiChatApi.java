package staraxis.webnet.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import staraxis.webnet.auth.AuthStore;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AiChatApi
 *
 * 作用：
 * - 为前端 AI 助手浮动球提供对话接口（/api/ai/chat）。
 * - 验证玩家身份，将玩家 token 传递给 AI 系统，实现权限绑定。
 * - 将请求代理转发到 AI 系统的 HTTP 服务器（默认 127.0.0.1:17891）。
 * - 支持流式/非流式对话，维护对话上下文。
 * - 自动触发 AI 系统启动，并等待其就绪。
 *
 * 安全设计：
 * - 前端必须传递有效的玩家 Authorization token
 * - 后端验证玩家身份后才允许访问 AI 系统
 * - AI 系统使用玩家 token 进行 WebSocket 连接，确保数据访问权限与玩家一致
 *
 * 请求格式：
 * POST /api/ai/chat
 * Header: Authorization: Bearer <player_token>
 * {
 * "messages": [
 * {"role": "user", "content": "你好"}
 * ],
 * "context": {
 * "playerId": "...",
 * "playerToken": "..." // 玩家 token，AI 系统用它进行 WebSocket 认证
 * },
 * "show_thinking": true
 * }
 *
 * 响应格式：
 * {
 * "ok": true,
 * "message": "AI回复内容",
 * "thinking": [...],
 * "usage": {"prompt_tokens": 200, "completion_tokens": 100, "total_tokens":
 * 300},
 * "tool_calls_count": 1,
 * "total_duration_ms": 2500,
 * "provider": "openai",
 * "model": "gpt-4"
 * }
 */
public class AiChatApi {
    private final ObjectMapper objectMapper;
    private final AuthStore authStore;
    private final String aiHost;
    private final int aiPort;
    private static final int MAX_STARTUP_WAIT_MS = 30000;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 120000;

    public AiChatApi(ObjectMapper objectMapper, AuthStore authStore) {
        this(objectMapper, authStore, "127.0.0.1", 17891);
    }

    public AiChatApi(ObjectMapper objectMapper, AuthStore authStore, String aiHost, int aiPort) {
        this.objectMapper = objectMapper;
        this.authStore = authStore;
        this.aiHost = aiHost;
        this.aiPort = aiPort;
    }

    public HttpHandler createHandler() {
        return exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                exchange.setStatusCode(405);
                sendJson(exchange, Map.of("ok", false, "error", "method_not_allowed"));
                return;
            }

            exchange.dispatch(() -> {
                try {
                    // 1. 验证玩家身份
                    String auth = exchange.getRequestHeaders().get("Authorization") != null
                            && !exchange.getRequestHeaders().get("Authorization").isEmpty()
                                    ? exchange.getRequestHeaders().get("Authorization").get(0)
                                    : null;

                    AuthStore.Session session = authStore.getSessionFromAuthorizationHeader(auth);
                    if (session == null) {
                        exchange.setStatusCode(401);
                        sendJson(exchange, Map.of("ok", false, "error", "unauthorized", "message", "请先登录后再使用 AI 助手"));
                        return;
                    }

                    staraxis.webnet.core.WebNetLog
                            .log("AI Chat: player=" + session.username + " playerId=" + session.playerId);

                    // 2. 确保 AI 系统已启动
                    WebAiAutoStarter.ensureAiStartedIfNeeded();

                    // 3. 等待 AI HTTP 服务器就绪
                    if (!waitForAiReady()) {
                        staraxis.webnet.core.WebNetLog
                                .log("AI system failed to start within " + MAX_STARTUP_WAIT_MS + "ms");
                        exchange.setStatusCode(503);
                        sendJson(exchange,
                                Map.of("ok", false, "error", "AI system is starting, please try again later"));
                        return;
                    }

                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                    // 4. 解析请求以验证格式
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body, Map.class);

                    if (!req.containsKey("messages")) {
                        exchange.setStatusCode(400);
                        sendJson(exchange, Map.of("ok", false, "error", "messages is required"));
                        return;
                    }

                    // 5. 更新 context 中的 playerToken，确保使用当前登录玩家的 token
                    @SuppressWarnings("unchecked")
                    Map<String, Object> context = (Map<String, Object>) req.computeIfAbsent("context",
                            k -> new java.util.HashMap<>());
                    context.put("playerToken", auth); // 传递完整的 Authorization header
                    context.put("playerId", session.playerId);
                    context.put("username", session.username);

                    // 重新序列化请求体
                    String modifiedBody = objectMapper.writeValueAsString(req);

                    // 6. 转发到 AI 系统 HTTP 服务器
                    String aiUrl = "http://" + aiHost + ":" + aiPort + "/api/chat";
                    String response = forwardToAiSystem(aiUrl, modifiedBody);

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(response);

                } catch (Exception e) {
                    staraxis.webnet.core.WebNetLog.log("AI chat error: " + e.getMessage());
                    exchange.setStatusCode(500);
                    try {
                        sendJson(exchange, Map.of("ok", false, "error", e.getMessage()));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        };
    }

    /**
     * 等待 AI HTTP 服务器就绪
     */
    private boolean waitForAiReady() {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + MAX_STARTUP_WAIT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (isAiPortOpen()) {
                long elapsed = System.currentTimeMillis() - startTime;
                staraxis.webnet.core.WebNetLog.log("AI HTTP server is ready after " + elapsed + "ms");
                return true;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    /**
     * 检查 AI HTTP 服务器端口是否已开放
     */
    private boolean isAiPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(aiHost, aiPort), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String forwardToAiSystem(String aiUrl, String requestBody) throws Exception {
        URL url = new URL(aiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        try {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

            if (is == null) {
                throw new Exception("AI system returned empty response with status: " + status);
            }

            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (status < 200 || status >= 300) {
                throw new Exception("AI system error (" + status + "): " + response);
            }

            return response;
        } finally {
            conn.disconnect();
        }
    }

    private void sendJson(HttpServerExchange exchange, Map<String, Object> data) throws Exception {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send(objectMapper.writeValueAsString(data));
    }
}
