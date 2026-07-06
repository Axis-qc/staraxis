package staraxis.webnet.api.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import staraxis.game.StarAxisGameRuntime;
import staraxis.webnet.auth.AuthStore;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.game.GameSessions;
import staraxis.webnet.websocket.SnapshotMessageFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SnapshotRoutes（快照查询路由挂载器）喵。
 *
 * 作用喵：
 * - 将 WebNetServer 中的 /api/snapshot/* 具体实现下沉到独立模块喵。
 * - WebNetServer 仅负责挂载路由与服务器生命周期，不再承载具体业务实现喵。
 */
public final class SnapshotRoutes {

    private SnapshotRoutes() {
    }

    /**
     * 注册 /snapshot/* 路由喵。
     * 注意喵：这里注册的是 apiHandler 下的相对路径，最终路径为 /api/snapshot/* 喵。
     */
    public static void register(PathHandler apiHandler, ObjectMapper objectMapper, AuthStore authStore,
            WsConnectionManager connMgr, java.util.function.LongSupplier tickCostMsSupplier) {
        if (apiHandler == null) {
            return;
        }

        apiHandler.addExactPath("/snapshot/latest",
                createLatestHandler(objectMapper, authStore, connMgr, tickCostMsSupplier));
        apiHandler.addExactPath("/snapshot/meta", createMetaHandler(objectMapper, authStore, connMgr));
        apiHandler.addExactPath("/snapshot/entity", createEntityHandler(objectMapper, authStore, connMgr));
        apiHandler.addExactPath("/snapshot/owned/search", createOwnedSearchHandler(objectMapper, authStore, connMgr));
    }

    private static HttpHandler createLatestHandler(ObjectMapper objectMapper, AuthStore authStore,
            WsConnectionManager connMgr, java.util.function.LongSupplier tickCostMsSupplier) {
        return exchange -> exchange.dispatch(() -> {
            try {
                String auth = exchange.getRequestHeaders().get("Authorization") != null
                        && !exchange.getRequestHeaders().get("Authorization").isEmpty()
                                ? exchange.getRequestHeaders().get("Authorization").get(0)
                                : null;

                AuthStore.Session session = authStore.getSessionFromAuthorizationHeader(auth);
                if (session == null) {
                    exchange.setStatusCode(401);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender()
                            .send(objectMapper.writeValueAsString(Map.of("ok", false, "error", "unauthorized")));
                    return;
                }

                StarAxisGameRuntime runtime = GameSessions.getRuntime();
                if (runtime == null) {
                    exchange.setStatusCode(503);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    exchange.getResponseSender()
                            .send(objectMapper.writeValueAsString(Map.of("ok", false, "error", "world_not_created")));
                    return;
                }

                String nationId = runtime.getWorldStateForSimOnly().nationManager.getNationIdByPlayer(session.playerId);
                if (nationId == null) {
                    nationId = connMgr.getPlayerNationId(session.playerId);
                }

                long tickCostMs = tickCostMsSupplier == null ? 0 : tickCostMsSupplier.getAsLong();

                var snapshotDto = SnapshotMessageFactory.buildSnapshotMessageWithNation(runtime, tickCostMs, null,

                        nationId);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

                exchange.getResponseSender().send(objectMapper.writeValueAsString(snapshotDto));

            } catch (Exception e) {

                exchange.setStatusCode(500);

                try {

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

                    exchange.getResponseSender()

                            .send(objectMapper.writeValueAsString(Map.of("ok", false, "error", e.getMessage())));

                } catch (Exception ignored) {

                    exchange.endExchange();

                }

            }

        });

    }

    private static HttpHandler createMetaHandler(ObjectMapper objectMapper, AuthStore authStore,

            WsConnectionManager connMgr) {

        return exchange -> exchange.dispatch(() -> {

            try {

                String auth = exchange.getRequestHeaders().get("Authorization") != null

                        && !exchange.getRequestHeaders().get("Authorization").isEmpty()

                                ? exchange.getRequestHeaders().get("Authorization").get(0)

                                : null;

                AuthStore.Session session = authStore.getSessionFromAuthorizationHeader(auth);

                if (session == null) {

                    exchange.setStatusCode(401);

                    exchange.endExchange();

                    return;

                }

                StarAxisGameRuntime runtime = GameSessions.getRuntime();

                if (runtime == null) {

                    exchange.setStatusCode(503);

                    exchange.endExchange();

                    return;

                }

                String nationId = runtime.getWorldStateForSimOnly().nationManager.getNationIdByPlayer(session.playerId);

                if (nationId == null) {

                    nationId = connMgr.getPlayerNationId(session.playerId);

                }

                var rt = runtime.getRealTimeWorldStateReadonly();

                Map<String, Integer> ownedEntityCounts = new HashMap<>();

                if (nationId != null && !nationId.isBlank()) {

                    for (var s : rt.getEntitySnapshotsView()) {

                        String owner = null;

                        try {

                            owner = SnapshotMessageFactory.extractOwnerNationId(s);

                        } catch (Exception ignored) {

                        }

                        if (nationId.equals(owner)) {

                            String tn = s.entityType == null ? "null" : s.entityType.name();

                            ownedEntityCounts.put(tn, ownedEntityCounts.getOrDefault(tn, 0) + 1);

                        }

                    }

                }

                Map<String, Object> resp = new LinkedHashMap<>();

                resp.put("ok", true);
                resp.put("nationId", nationId);
                resp.put("simulationTick", rt.simulationTick);

                resp.put("totalGameSeconds", rt.totalGameSeconds);

                resp.put("deltaGameSeconds", rt.deltaGameSeconds);

                resp.put("worldRadius", rt.worldRadius);

                resp.put("ownedEntityCounts", ownedEntityCounts);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

                exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));

            } catch (Exception e) {

                exchange.setStatusCode(500);

                exchange.endExchange();

            }

        });

    }

    private static HttpHandler createEntityHandler(ObjectMapper objectMapper, AuthStore authStore,

            WsConnectionManager connMgr) {

        return exchange -> exchange.dispatch(() -> {

            try {

                String idStr = exchange.getQueryParameters().get("id") != null

                        ? exchange.getQueryParameters().get("id").peekFirst()

                        : null;

                if (idStr == null || idStr.isBlank()) {

                    exchange.setStatusCode(400);

                    exchange.endExchange();

                    return;

                }

                long entityId = Long.parseLong(idStr);

                String auth = exchange.getRequestHeaders().get("Authorization") != null

                        && !exchange.getRequestHeaders().get("Authorization").isEmpty()

                                ? exchange.getRequestHeaders().get("Authorization").get(0)

                                : null;

                AuthStore.Session session = authStore.getSessionFromAuthorizationHeader(auth);

                if (session == null) {

                    exchange.setStatusCode(401);

                    exchange.endExchange();

                    return;

                }

                StarAxisGameRuntime runtime = GameSessions.getRuntime();

                if (runtime == null) {

                    exchange.setStatusCode(503);

                    exchange.endExchange();

                    return;

                }

                String nationId = runtime.getWorldStateForSimOnly().nationManager.getNationIdByPlayer(session.playerId);

                if (nationId == null) {

                    nationId = connMgr.getPlayerNationId(session.playerId);

                }

                staraxis.game.state.snapshot.EntitySnapshot found = null;

                for (var s : runtime.getRealTimeWorldStateReadonly().getEntitySnapshotsView()) {

                    if (s != null && s.entityId == entityId) {

                        found = s;

                        break;

                    }

                }

                if (found == null) {

                    exchange.setStatusCode(404);

                    exchange.endExchange();

                    return;

                }

                String owner = null;

                try {

                    owner = SnapshotMessageFactory.extractOwnerNationId(found);

                } catch (Exception ignored) {

                }

                boolean owned = nationId != null && !nationId.isBlank() && nationId.equals(owner);

                boolean isNatural = found.entityType == staraxis.game.entity.EntityType.STAR

                        || found.entityType == staraxis.game.entity.EntityType.PLANET

                        || found.entityType == staraxis.game.entity.EntityType.SYSTEM_BARYCENTER

                        || found.entityType == staraxis.game.entity.EntityType.ASTEROID

                        || found.entityType == staraxis.game.entity.EntityType.MOON;

                if (!owned && !isNatural) {

                    exchange.setStatusCode(403);

                    exchange.endExchange();

                    return;

                }

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

                exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(

                        "ok", true,

                        "entity", found)));

            } catch (Exception e) {

                exchange.setStatusCode(500);

                exchange.endExchange();

            }

        });

    }

    private static HttpHandler createOwnedSearchHandler(ObjectMapper objectMapper, AuthStore authStore,

            WsConnectionManager connMgr) {

        return exchange -> exchange.dispatch(() -> {

            try {

                String auth = exchange.getRequestHeaders().get("Authorization") != null

                        && !exchange.getRequestHeaders().get("Authorization").isEmpty()

                                ? exchange.getRequestHeaders().get("Authorization").get(0)

                                : null;

                AuthStore.Session session = authStore.getSessionFromAuthorizationHeader(auth);

                if (session == null) {

                    exchange.setStatusCode(401);

                    exchange.endExchange();

                    return;

                }

                StarAxisGameRuntime runtime = GameSessions.getRuntime();

                if (runtime == null) {

                    exchange.setStatusCode(503);

                    exchange.endExchange();

                    return;

                }

                String nationId = runtime.getWorldStateForSimOnly().nationManager.getNationIdByPlayer(session.playerId);

                if (nationId == null) {

                    nationId = connMgr.getPlayerNationId(session.playerId);

                }

                if (nationId == null || nationId.isBlank()) {

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(

                            "ok", true,

                            "total", 0,

                            "items", List.of())));

                    return;

                }

                String entityType = exchange.getQueryParameters().get("entityType") != null

                        ? exchange.getQueryParameters().get("entityType").peekFirst()

                        : null;

                String text = exchange.getQueryParameters().get("text") != null

                        ? exchange.getQueryParameters().get("text").peekFirst()

                        : null;

                String systemIdStr = exchange.getQueryParameters().get("systemId") != null

                        ? exchange.getQueryParameters().get("systemId").peekFirst()

                        : null;

                int limit = 50;

                int offset = 0;

                try {

                    if (exchange.getQueryParameters().get("limit") != null) {

                        limit = Integer.parseInt(exchange.getQueryParameters().get("limit").peekFirst());

                    }

                    if (exchange.getQueryParameters().get("offset") != null) {

                        offset = Integer.parseInt(exchange.getQueryParameters().get("offset").peekFirst());

                    }

                } catch (Exception ignored) {

                }

                if (limit < 1)

                    limit = 1;

                if (limit > 200)

                    limit = 200;

                if (offset < 0)

                    offset = 0;

                Long systemIdFilter = null;

                try {

                    if (systemIdStr != null && !systemIdStr.isBlank()) {

                        systemIdFilter = Long.parseLong(systemIdStr);

                    }

                } catch (Exception ignored) {

                }

                final String nationIdFinal = nationId;

                final String textLower = text == null ? null : text.toLowerCase();

                final String typeLower = entityType == null ? null : entityType.toLowerCase();

                ArrayList<staraxis.game.state.snapshot.EntitySnapshot> matches = new ArrayList<>();

                for (var s : runtime.getRealTimeWorldStateReadonly().getEntitySnapshotsView()) {

                    if (s == null) {

                        continue;

                    }

                    String owner = null;

                    try {

                        owner = SnapshotMessageFactory.extractOwnerNationId(s);

                    } catch (Exception ignored) {

                    }

                    if (!nationIdFinal.equals(owner)) {

                        continue;

                    }

                    if (typeLower != null && s.entityType != null

                            && !s.entityType.name().toLowerCase().equals(typeLower)) {

                        continue;

                    }

                    if (systemIdFilter != null) {

                        if (s.systemId != systemIdFilter) {

                            continue;

                        }

                    }

                    if (textLower != null && !textLower.isBlank()) {

                        boolean ok = false;

                        if (String.valueOf(s.entityId).contains(textLower)) {

                            ok = true;

                        } else if (s.details != null) {

                            try {

                                String detailsJson = objectMapper.writeValueAsString(s.details).toLowerCase();

                                if (detailsJson.contains(textLower)) {

                                    ok = true;

                                }

                            } catch (Exception ignored) {

                            }

                        }

                        if (!ok) {

                            continue;

                        }

                    }

                    matches.add(s);

                }

                int total = matches.size();

                List<staraxis.game.state.snapshot.EntitySnapshot> paged = matches;

                if (offset > 0 || limit < matches.size()) {

                    int from = Math.min(offset, matches.size());

                    int to = Math.min(from + limit, matches.size());

                    paged = matches.subList(from, to);

                }

                Map<String, Object> resp = new LinkedHashMap<>();

                resp.put("ok", true);

                resp.put("total", total);

                resp.put("items", paged);

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

                exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));

            } catch (Exception e) {

                exchange.setStatusCode(500);

                exchange.endExchange();

            }

        });

    }

}
