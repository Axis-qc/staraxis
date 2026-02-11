package staraxis.webnet.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import staraxis.webnet.core.GameLog;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AuthApi
 *
 * 作用：
 * - 提供账号认证相关的 HTTP API（注册、登录、信息查询、注销、更新 gameId）。
 * - 对应路由前缀：/api/auth
 *
 * 提供的接口：
 * - POST /register : 注册新账号
 * - POST /login : 登录并获取 token
 * - GET /me : 获取当前会话账号信息
 * - POST /logout : 注销当前会话
 * - POST /gameId : 更新当前账号的 gameId
 *
 * 设计说明：
 * - 使用 B 方案：依赖 AuthStore 和 ObjectMapper，并记录关键行为日志。
 * - 线程模型：涉及阻塞 IO（JSON 解析、文件读写），使用 exchange.dispatch 切换到 worker 线程。
 */
public class AuthApi {

    private final AuthStore authStore;
    private final ObjectMapper objectMapper;

    public AuthApi(AuthStore authStore, ObjectMapper objectMapper) {
        this.authStore = authStore;
        this.objectMapper = objectMapper;
    }

    public HttpHandler createHandler() {
        return Handlers.path()
                .addExactPath("/register", this::handleRegister)
                .addExactPath("/login", this::handleLogin)
                .addExactPath("/me", this::handleMe)
                .addExactPath("/logout", this::handleLogout)
                .addExactPath("/gameId", this::handleSetGameId);
    }

    private void handleRegister(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                sendError(exchange, 405, "method_not_allowed");
                return;
            }
            try {
                exchange.startBlocking();
                String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> req = objectMapper.readValue(body, Map.class);
                String user = String.valueOf(req.get("username"));
                String pass = String.valueOf(req.get("password"));

                authStore.register(user, pass);
                GameLog.log("auth_register user=" + user);
                sendOk(exchange, Map.of("ok", true, "username", user));
            } catch (Exception e) {
                GameLog.log("auth_register_failed: " + e.getMessage());
                sendError(exchange, 400, e.getMessage());
            }
        });
    }

    private void handleLogin(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                sendError(exchange, 405, "method_not_allowed");
                return;
            }
            try {
                exchange.startBlocking();
                String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> req = objectMapper.readValue(body, Map.class);
                String user = String.valueOf(req.get("username"));
                String pass = String.valueOf(req.get("password"));

                AuthStore.Session s = authStore.login(user, pass);
                GameLog.log("auth_login user=" + user + " playerId=" + s.playerId);
                sendOk(exchange, Map.of("ok", true, "token", s.token, "playerId", s.playerId));
            } catch (Exception e) {
                GameLog.log("auth_login_failed: " + e.getMessage());
                sendError(exchange, 401, e.getMessage());
            }
        });
    }

    private void handleMe(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            try {
                String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
                AuthStore.Session s = authStore.getSessionFromAuthorizationHeader(auth);
                if (s == null) {
                    sendError(exchange, 401, "unauthorized");
                    return;
                }
                AuthStore.Account a = authStore.loadAccount(s.username);
                if (a == null) {
                    sendError(exchange, 404, "account_not_found");
                    return;
                }
                sendOk(exchange, Map.of(
                        "ok", true,
                        "username", a.username,
                        "playerId", a.playerId,
                        "gameId", a.gameId == null ? "" : a.gameId,
                        "role", a.role == null ? "USER" : a.role));
            } catch (Exception e) {
                sendError(exchange, 500, e.getMessage());
            }
        });
    }

    private void handleLogout(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {
                authStore.logout(auth.substring(7).trim());
            }
            sendOk(exchange, Map.of("ok", true));
        });
    }

    private void handleSetGameId(HttpServerExchange exchange) {
        exchange.dispatch(() -> {
            try {
                exchange.startBlocking();
                String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
                AuthStore.Session s = authStore.getSessionFromAuthorizationHeader(auth);
                if (s == null) {
                    sendError(exchange, 401, "unauthorized");
                    return;
                }

                String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> req = objectMapper.readValue(body, Map.class);
                String gameId = req.get("gameId") == null ? "" : String.valueOf(req.get("gameId"));

                authStore.setGameId(s.playerId, gameId);
                GameLog.log("auth_set_gameid playerId=" + s.playerId + " gameId=" + gameId);
                sendOk(exchange, Map.of("ok", true, "playerId", s.playerId, "gameId", gameId.trim()));
            } catch (Exception e) {
                GameLog.log("auth_set_gameid_failed: " + e.getMessage());
                sendError(exchange, 400, e.getMessage());
            }
        });
    }

    private void sendOk(HttpServerExchange exchange, Object data) {
        try {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(objectMapper.writeValueAsString(data));
        } catch (Exception ignored) {
            exchange.endExchange();
        }
    }

    private void sendError(HttpServerExchange exchange, int code, String msg) {
        exchange.setStatusCode(code);
        try {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender()
                    .send(objectMapper.writeValueAsString(Map.of("ok", false, "error", String.valueOf(msg))));
        } catch (Exception ignored) {
            exchange.endExchange();
        }
    }
}
