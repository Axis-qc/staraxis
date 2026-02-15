package staraxis.webnet.api.nation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * NationRoutes（国家相关路由挂载器）喵。
 *
 * 作用喵：
 * - 将 WebNetServer 中与国家预设、玩家国家存档相关的 HTTP 路由下沉到独立模块喵。
 * - WebNetServer 仅负责挂载，不负责具体实现细节喵。
 */
public final class NationRoutes {

    private NationRoutes() {
    }

    /**
     * 注册 nation 相关路由喵。
     * 注意喵：apiHandler 为 /api 下的子 handler，因此最终路径为 /api/game/nations 与
     * /api/nations/players/* 喵。
     */
    public static void register(PathHandler apiHandler, ObjectMapper objectMapper) {
        if (apiHandler == null) {
            return;
        }

        apiHandler.addExactPath("/game/nations", exchange -> {
            exchange.dispatch(() -> {
                try {
                    NationPresetsApi.setJsonContentType(exchange);
                    List<staraxis.game.nation.NationDef> nations = NationPresetsApi.loadAllPresetNations(objectMapper);
                    exchange.getResponseSender()
                            .send(objectMapper.writeValueAsString(NationPresetsApi.toResponse(nations)));
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/nations/players/list", exchange -> {
            exchange.dispatch(() -> {
                try {
                    PlayerNationApi.setJsonContentType(exchange);
                    String username = PlayerNationApi.query(exchange, "username");
                    String playerId = PlayerNationApi.query(exchange, "playerId");
                    String json = PlayerNationApi.handleList(objectMapper,
                            new staraxis.webnet.repo.nation.PlayerNationFileRepository(objectMapper), username,
                            playerId);
                    exchange.getResponseSender().send(json);
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/nations/players/get", exchange -> {
            exchange.dispatch(() -> {
                try {
                    PlayerNationApi.setJsonContentType(exchange);
                    String username = PlayerNationApi.query(exchange, "username");
                    String playerId = PlayerNationApi.query(exchange, "playerId");
                    String nationId = PlayerNationApi.query(exchange, "nationId");
                    String json = PlayerNationApi.handleGet(objectMapper,
                            new staraxis.webnet.repo.nation.PlayerNationFileRepository(objectMapper), username,
                            playerId,
                            nationId);
                    exchange.getResponseSender().send(json);
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper
                                .writeValueAsString(Map.of("ok", false, "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/nations/players/save", exchange -> {
            exchange.dispatch(() -> {
                PlayerNationApi.setJsonContentType(exchange);
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
                    String json = PlayerNationApi.handleSave(objectMapper,
                            new staraxis.webnet.repo.nation.PlayerNationFileRepository(objectMapper), body);
                    exchange.getResponseSender().send(json);
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
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
