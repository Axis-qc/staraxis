package staraxis.webnet.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import staraxis.webnet.auth.AuthStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * AdminApi
 *
 * 作用：
 * - 提供服务管理相关的 HTTP API（状态监控、Ping、进程退出）。
 * - 对应路由：/api/status, /api/ping, /api/quit
 *
 * 提供的接口：
 * - GET /status : 返回服务器运行状态、连接数、WebUI 部署情况等喵。
 * - GET /ping : 简单的延迟测试接口喵。
 * - POST /quit : 请求关闭服务端进程（需 ADMIN 权限）喵。
 */
public class AdminApi {

    public interface AdminActions {
        void restart();
    }

    private final WebNetServerConfig config;
    private final AuthStore authStore;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.atomic.AtomicInteger playerConnectionCount;
    private final java.util.concurrent.atomic.AtomicInteger aiConnectionCount;
    private final java.util.concurrent.atomic.AtomicLong lastDisconnectAtMs;
    private final AdminActions actions;

    public AdminApi(WebNetServerConfig config, AuthStore authStore, ObjectMapper objectMapper,
            java.util.concurrent.atomic.AtomicInteger playerConnectionCount,
            java.util.concurrent.atomic.AtomicInteger aiConnectionCount,
            java.util.concurrent.atomic.AtomicLong lastDisconnectAtMs,
            AdminActions actions) {
        this.config = config;
        this.authStore = authStore;
        this.objectMapper = objectMapper;
        this.playerConnectionCount = playerConnectionCount;
        this.aiConnectionCount = aiConnectionCount;
        this.lastDisconnectAtMs = lastDisconnectAtMs;
        this.actions = actions;
    }

    public HttpHandler createHandler() {
        return Handlers.path()
                .addExactPath("/status", this::handleStatus)
                .addExactPath("/ping", this::handlePing)
                .addExactPath("/quit", this::handleQuit)
                .addExactPath("/restart", this::handleRestart);
    }

    private void handleStatus(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        int pc = playerConnectionCount.get();
        int aic = aiConnectionCount.get();
        long last = lastDisconnectAtMs.get();
        long idleMs = last > 0 ? (System.currentTimeMillis() - last) : 0;

        File webUi = new File("webui");
        boolean webUiExists = webUi.exists() && webUi.isDirectory();
        long webUiLastModified = webUiExists ? webUi.lastModified() : 0L;
        boolean webUiIndexExists = webUiExists && new File(webUi, "index.html").isFile();

        long webUiFileCount = 0;
        long webUiTotalBytes = 0;
        if (webUiExists) {
            try (Stream<Path> s = Files.walk(webUi.toPath())) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    if (Files.isRegularFile(p)) {
                        webUiFileCount++;
                        try {
                            webUiTotalBytes += Files.size(p);
                        } catch (IOException ignored) {
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        try {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("host", config.host);
            out.put("port", config.port);
            out.put("playerConnections", pc);
            out.put("aiConnections", aic);
            out.put("connections", pc); // 保持兼容性喵
            out.put("autoExitSeconds", config.autoExitSeconds);
            out.put("idleSeconds", (idleMs / 1000));
            out.put("webUiExists", webUiExists);
            out.put("webUiIndexExists", webUiIndexExists);
            out.put("webUiLastModifiedMs", webUiLastModified);
            out.put("webUiFileCount", webUiFileCount);
            out.put("webUiTotalBytes", webUiTotalBytes);

            String json = objectMapper.writeValueAsString(out);
            exchange.getResponseSender().send(json);
        } catch (Exception e) {
            exchange.setStatusCode(500).endExchange();
        }
    }

    private void handlePing(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send("{\"serverTimeMs\":" + System.currentTimeMillis() + "}");
    }

    private void handleRestart(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            WebNetLog.logThrottled("admin_restart", "HTTP restart requested: " + exchange.getRequestMethod() + " "
                    + exchange.getRequestPath());
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

            String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
            AuthStore.Session s = authStore.getSessionFromAuthorizationHeader(auth);
            if (s == null) {
                exchange.setStatusCode(401);
                exchange.getResponseSender().send("{\"ok\":false,\"error\":\"unauthorized\"}");
                return;
            }

            AuthStore.Account a = authStore.loadAccount(s.username);
            String role = a != null && a.role != null && !a.role.isBlank() ? a.role : "USER";
            if (!"ADMIN".equalsIgnoreCase(role)) {
                exchange.setStatusCode(403);
                exchange.getResponseSender().send("{\"ok\":false,\"error\":\"forbidden\"}");
                return;
            }

            if (actions == null) {
                exchange.setStatusCode(500);
                exchange.getResponseSender().send("{\"ok\":false,\"error\":\"restart_not_supported\"}");
                return;
            }

            exchange.getResponseSender().send("{\"ok\":true}");
            exchange.endExchange();

            Thread t = new Thread(actions::restart, "webnet-restart");
            t.setDaemon(false);
            t.start();
        });
    }

    private void handleQuit(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            WebNetLog.logThrottled("admin_quit", "HTTP quit requested: " + exchange.getRequestMethod() + " "
                    + exchange.getRequestPath());
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

            String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
            AuthStore.Session s = authStore.getSessionFromAuthorizationHeader(auth);
            if (s == null) {
                exchange.setStatusCode(401);
                exchange.getResponseSender().send("{\"ok\":false,\"error\":\"unauthorized\"}");
                return;
            }

            AuthStore.Account a = authStore.loadAccount(s.username);
            String role = a != null && a.role != null && !a.role.isBlank() ? a.role : "USER";
            if (!"ADMIN".equalsIgnoreCase(role)) {
                exchange.setStatusCode(403);
                exchange.getResponseSender().send("{\"ok\":false,\"error\":\"forbidden\"}");
                return;
            }

            exchange.getResponseSender().send("{\"ok\":true}");
            exchange.endExchange();

            // 延迟退出，确保响应发送出去喵
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                WebNetLog.logThrottled("admin_quit_exit", "WebNetServer exiting by HTTP quit...");
                System.exit(0);
            }).start();
        });
    }
}
