package staraxis.webnet.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AiUsageApi
 *
 * 作用：
 * - 获取 AI 系统的累计 Token 使用统计（/api/ai/usage）。
 * - 从 AI 系统的 HTTP 服务器获取统计信息并返回给前端。
 *
 * 响应格式：
 * {
 * "ok": true,
 * "usage": {
 * "session_prompt_tokens": 5000,
 * "session_completion_tokens": 2000,
 * "session_total_tokens": 7000,
 * "request_count": 10,
 * "tool_call_count": 5,
 * "avg_tokens_per_request": 700
 * }
 * }
 */
public class AiUsageApi {
    private final ObjectMapper objectMapper;
    private final String aiHost;
    private final int aiPort;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 10000;

    public AiUsageApi(ObjectMapper objectMapper) {
        this(objectMapper, "127.0.0.1", 17891);
    }

    public AiUsageApi(ObjectMapper objectMapper, String aiHost, int aiPort) {
        this.objectMapper = objectMapper;
        this.aiHost = aiHost;
        this.aiPort = aiPort;
    }

    public HttpHandler createHandler() {
        return exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                exchange.setStatusCode(405);
                sendJson(exchange, Map.of("ok", false, "error", "method_not_allowed"));
                return;
            }

            exchange.dispatch(() -> {
                try {
                    // 检查 AI 系统是否可用
                    if (!isAiPortOpen()) {
                        exchange.setStatusCode(503);
                        sendJson(exchange, Map.of(
                                "ok", false,
                                "error", "AI system not available",
                                "usage", Map.of(
                                        "session_prompt_tokens", 0,
                                        "session_completion_tokens", 0,
                                        "session_total_tokens", 0,
                                        "request_count", 0,
                                        "tool_call_count", 0,
                                        "avg_tokens_per_request", 0)));
                        return;
                    }

                    // 从 AI 系统获取统计
                    String aiUrl = "http://" + aiHost + ":" + aiPort + "/api/usage";
                    String response = fetchFromAiSystem(aiUrl);

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender().send(response);

                } catch (Exception e) {
                    staraxis.webnet.core.WebNetLog.log("AI usage error: " + e.getMessage());
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

    private String fetchFromAiSystem(String aiUrl) throws Exception {
        URL url = new URL(aiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

        try {
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
