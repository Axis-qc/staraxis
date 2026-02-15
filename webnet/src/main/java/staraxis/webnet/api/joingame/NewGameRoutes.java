package staraxis.webnet.api.joingame;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import staraxis.webnet.api.newgame.NewGameApi;
import staraxis.webnet.api.newgame.NewGameDraftRepository;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * NewGameRoutes（新游戏/加入游戏路由挂载器）喵。
 *
 * 作用喵：
 * - 将 WebNetServer 中与创建新游戏、加入游戏流程相关的 HTTP 路由下沉到独立模块喵。
 * - WebNetServer 仅负责挂载，不负责具体流程的串联实现喵。
 */
public final class NewGameRoutes {

    private NewGameRoutes() {
    }

    /**
     * 注册 join-game 和 newgame 相关路由喵。
     */
    public static void register(PathHandler apiHandler, ObjectMapper objectMapper) {
        if (apiHandler == null) {
            return;
        }

        // --- Join Game Routes ---

        apiHandler.addExactPath("/join-game/available-spawns", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, NewGameApi.jsonContentType());
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(
                                objectMapper.writeValueAsString(Map.of("ok", false, "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    JoinGameApi.setJsonContentType(exchange);
                    Map<String, Object> resp = JoinGameApi.handleAvailableSpawns();
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/join-game/confirm-spawn", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, NewGameApi.jsonContentType());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(
                                objectMapper.writeValueAsString(Map.of("ok", false, "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> req = NewGameApi.parseBodyToMap(objectMapper, body);
                    JoinGameApi.setJsonContentType(exchange);
                    Map<String, Object> resp = JoinGameApi.handleConfirmSpawn(objectMapper, req);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        // --- New Game Routes ---

        apiHandler.addExactPath("/newgame/step1/selectNation", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, NewGameApi.jsonContentType());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(
                                objectMapper.writeValueAsString(Map.of("ok", false, "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> req = NewGameApi.parseBodyToMap(objectMapper, body);
                    NewGameDraftRepository repo = new NewGameDraftRepository(objectMapper);
                    Map<String, Object> resp = NewGameApi.step1SelectNation(objectMapper, repo, req);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/newgame/step2/worldSettings", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, NewGameApi.jsonContentType());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(
                                objectMapper.writeValueAsString(Map.of("ok", false, "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> req = NewGameApi.parseBodyToMap(objectMapper, body);
                    NewGameDraftRepository repo = new NewGameDraftRepository(objectMapper);
                    Map<String, Object> resp = NewGameApi.step2WorldSettings(objectMapper, repo, req);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/newgame/step3/confirm", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, NewGameApi.jsonContentType());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(
                                objectMapper.writeValueAsString(Map.of("ok", false, "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> req = NewGameApi.parseBodyToMap(objectMapper, body);
                    NewGameDraftRepository repo = new NewGameDraftRepository(objectMapper);
                    Map<String, Object> resp = NewGameApi.step3Confirm(objectMapper, repo, req);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });
    }
}
