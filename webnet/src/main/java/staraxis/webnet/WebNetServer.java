package staraxis.webnet;

/**
 * WebNetServer
 *
 * 作用：
 * - StarAxis Web 版本的本地权威服务端（HTTP + WebSocket）。
 * - 托管前端构建产物（/webui），并提供 /api/** 接口供 Vue 前端调用。
 *
 * 路由概览：
 * - WebSocket：
 *   - GET /ws
 *
 * - 静态资源：
 *   - GET /webui/**    （项目根目录 webui/ 下的构建产物）
 *   - GET /            （302 跳转到 /webui/）
 *
 * - API：
 *   - GET  /api/status        本地服务状态
 *   - GET  /api/ping          RTT/连通性测试
 *   - POST /api/quit          请求关闭本地服务端进程
 *   - GET  /api/i18n/**       i18n 语言包相关
 *
 * - 认证/账号（本地文件存储在 gamedata/accounts）：
 *   - POST /api/auth/register   注册（username 作为文件名）
 *   - POST /api/auth/login      登录（返回 token/playerId）
 *   - GET  /api/auth/me         获取当前会话信息
 *   - POST /api/auth/logout     注销
 *   - POST /api/auth/gameId     保存玩家游戏ID（gameId）
 *
 * - Mod 管理（本地文件存储在 gamedata/mods）：
 *   - GET  /api/mods            返回扫描到的 mods 列表 + 当前 order/disabled
 *   - POST /api/mods/order      保存 mods 顺序与禁用列表（回写 gamedata/mods/mod-order.json，保留未知字段）
 *
 * 重要注意事项（Undertow 阻塞 IO）：
 * - Undertow 的请求处理默认运行在 IO 线程中。
 * - 读取请求体（startBlocking/getInputStream）、文件读写（gamedata/**）、以及 JSON 序列化等都可能触发阻塞 IO。
 * - 如果在 IO 线程里做阻塞操作，会触发 UT000126 并导致请求 500。
 * - 因此本文件中对涉及阻塞操作的 handler 使用 exchange.dispatch(...) 切换到 worker 线程处理。
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.webnet.game.GameSessions;
import staraxis.webnet.websocket.SnapshotMessageFactory;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class WebNetServer {

    private void tickAndBroadcastSnapshots() {
        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            // 没有世界时：不主动广播，等待订阅方触发一次性错误回包
            return;
        }

        long t0 = System.nanoTime();
        try {
            runtime.update(0f);
        } catch (Exception e) {
            // 避免 tick 线程被异常打断
            return;
        } finally {
            long costMs = Math.max(0, (System.nanoTime() - t0) / 1_000_000L);
            lastTickCostMs.set(costMs);
        }

        if (snapshotSubscribers.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(
                    SnapshotMessageFactory.buildSnapshotMessage(runtime, lastTickCostMs.get()));
            for (WebSocketChannel ch : snapshotSubscribers) {
                if (ch != null && ch.isOpen()) {
                    WebSockets.sendText(json, ch, null);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void sendSnapshotToChannel(WebSocketChannel channel) {
        if (channel == null || !channel.isOpen()) {
            return;
        }

        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            WebSockets.sendText(SnapshotMessageFactory.buildWorldNotCreatedJson(), channel, null);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(
                    SnapshotMessageFactory.buildSnapshotMessage(runtime, lastTickCostMs.get()));
            WebSockets.sendText(json, channel, null);
        } catch (Exception e) {
            WebSockets.sendText("{\"type\":\"snapshot\",\"ok\":false,\"error\":\"snapshot_build_failed\"}",
                    channel, null);
        }
    }

    private final WebNetServerConfig config;

    private Undertow undertow;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthStore authStore = new AuthStore(objectMapper);

    private final Set<WebSocketChannel> channels = ConcurrentHashMap.newKeySet();

    private final Set<WebSocketChannel> snapshotSubscribers = ConcurrentHashMap.newKeySet();

    private final AtomicLong lastTickCostMs = new AtomicLong(0);

    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final AtomicLong lastDisconnectAtMs = new AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "webnet-auto-exit");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService gameTicker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "webnet-game-ticker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean serverStarted;

    public WebNetServer(WebNetServerConfig config) {
        this.config = config;
    }

    public void start() {
        GameLog.initTruncate();
        GameLog.log("WebNetServer.start host=" + config.host + " port=" + config.port);

        PathHandler routes = Handlers.path();

        // Tick 驱动：单世界（global runtime），仅监控 tickCostMs，不做时间膨胀。
        gameTicker.scheduleAtFixedRate(this::tickAndBroadcastSnapshots, 0, 40, TimeUnit.MILLISECONDS);

        routes.addPrefixPath("/ws", Handlers.websocket((WebSocketHttpExchange exchange, WebSocketChannel channel) -> {
            channels.add(channel);
            connectionCount.incrementAndGet();

            System.out.println("WS connect: " + channel.getSourceAddress() + " connections=" + connectionCount.get());

            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                    String text = message.getData();
                    System.out.println("WS recv: " + text);

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = objectMapper.readValue(text, Map.class);
                        Object typeObj = m.get("type");
                        String type = typeObj == null ? null : String.valueOf(typeObj);

                        if ("subscribeSnapshot".equals(type)) {
                            snapshotSubscribers.add(channel);
                            boolean has = GameSessions.hasRuntime();
                            GameLog.log("WS subscribeSnapshot hasRuntime=" + has);
                            if (!has) {
                                WebSockets.sendText(
                                        "{\"type\":\"snapshot\",\"ok\":false,\"error\":\"world_not_created\"}",
                                        channel, null);
                            } else {
                                sendSnapshotToChannel(channel);
                            }
                            return;
                        }

                        if ("unsubscribeSnapshot".equals(type)) {
                            snapshotSubscribers.remove(channel);
                            WebSockets.sendText("{\"type\":\"unsubscribed\",\"ok\":true}", channel, null);
                            return;
                        }

                    } catch (Exception ignored) {
                    }

                    WebSockets.sendText(text, channel, null);
                }

                @Override
                protected void onClose(WebSocketChannel webSocketChannel,
                        io.undertow.websockets.core.StreamSourceFrameChannel frameChannel) {
                    channels.remove(webSocketChannel);
                    snapshotSubscribers.remove(webSocketChannel);
                    int left = connectionCount.decrementAndGet();
                    System.out.println("WS close: connections=" + left);
                    if (left <= 0) {
                        lastDisconnectAtMs.set(System.currentTimeMillis());
                    }
                }
            });

            channel.resumeReceives();
            WebSockets.sendText("{\"type\":\"hello\",\"server\":\"webnet\"}", channel, null);
        }));

        // --- API Routes ---
        PathHandler apiHandler = Handlers.path();

        // --- Game/Nations API ---
        apiHandler.addExactPath("/game/nations", exchange -> {
            exchange.dispatch(() -> {
                try {
                    staraxis.webnet.api.nation.NationPresetsApi.setJsonContentType(exchange);
                    List<staraxis.game.nation.NationDef> nations = staraxis.webnet.api.nation.NationPresetsApi
                            .loadAllPresetNations(objectMapper);
                    exchange.getResponseSender()
                            .send(objectMapper.writeValueAsString(staraxis.webnet.api.nation.NationPresetsApi
                                    .toResponse(nations)));
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        // --- Player Nations API ---
        apiHandler.addExactPath("/nations/players/list", exchange -> {
            exchange.dispatch(() -> {
                try {
                    staraxis.webnet.api.nation.PlayerNationApi.setJsonContentType(exchange);
                    String username = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "username");
                    String playerId = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "playerId");
                    String json = staraxis.webnet.api.nation.PlayerNationApi.handleList(objectMapper,
                            new staraxis.webnet.repo.nation.PlayerNationFileRepository(objectMapper),
                            username, playerId);
                    exchange.getResponseSender().send(json);
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/nations/players/get", exchange -> {
            exchange.dispatch(() -> {
                try {
                    staraxis.webnet.api.nation.PlayerNationApi.setJsonContentType(exchange);
                    String username = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "username");
                    String playerId = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "playerId");
                    String nationId = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "nationId");
                    String json = staraxis.webnet.api.nation.PlayerNationApi.handleGet(objectMapper,
                            new staraxis.webnet.repo.nation.PlayerNationFileRepository(objectMapper),
                            username, playerId, nationId);
                    exchange.getResponseSender().send(json);
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/nations/players/save", exchange -> {
            exchange.dispatch(() -> {
                staraxis.webnet.api.nation.PlayerNationApi.setJsonContentType(exchange);
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    String json = staraxis.webnet.api.nation.PlayerNationApi.handleSave(objectMapper,
                            new staraxis.webnet.repo.nation.PlayerNationFileRepository(objectMapper),
                            body);
                    exchange.getResponseSender().send(json);
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        // --- New Game API ---
        apiHandler.addExactPath("/newgame/step1/selectNation", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                        staraxis.webnet.api.newgame.NewGameApi.jsonContentType());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> req = staraxis.webnet.api.newgame.NewGameApi.parseBodyToMap(objectMapper, body);
                    staraxis.webnet.api.newgame.NewGameDraftRepository repo = new staraxis.webnet.api.newgame.NewGameDraftRepository(
                            objectMapper);
                    Map<String, Object> resp = staraxis.webnet.api.newgame.NewGameApi.step1SelectNation(objectMapper,
                            repo, req);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/newgame/step2/worldSettings", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                        staraxis.webnet.api.newgame.NewGameApi.jsonContentType());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> req = staraxis.webnet.api.newgame.NewGameApi.parseBodyToMap(objectMapper, body);
                    staraxis.webnet.api.newgame.NewGameDraftRepository repo = new staraxis.webnet.api.newgame.NewGameDraftRepository(
                            objectMapper);
                    Map<String, Object> resp = staraxis.webnet.api.newgame.NewGameApi.step2WorldSettings(objectMapper,
                            repo, req);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/newgame/step3/confirm", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                        staraxis.webnet.api.newgame.NewGameApi.jsonContentType());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, Object> req = staraxis.webnet.api.newgame.NewGameApi.parseBodyToMap(objectMapper, body);
                    staraxis.webnet.api.newgame.NewGameDraftRepository repo = new staraxis.webnet.api.newgame.NewGameDraftRepository(
                            objectMapper);
                    Map<String, Object> resp = staraxis.webnet.api.newgame.NewGameApi.step3Confirm(objectMapper, repo,
                            req);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(resp));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        // --- Auth API ---
        PathHandler authHandler = Handlers.path();

        authHandler.addExactPath("/register", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (body.isBlank()) {
                        body = "{}";
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body, Map.class);
                    String username = req.get("username") == null ? null : String.valueOf(req.get("username"));
                    String password = req.get("password") == null ? null : String.valueOf(req.get("password"));

                    AuthStore.Account a = authStore.register(username, password);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", true,
                            "playerId", a.playerId)));
                } catch (Exception e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        authHandler.addExactPath("/login", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (body.isBlank()) {
                        body = "{}";
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body, Map.class);
                    String username = req.get("username") == null ? null : String.valueOf(req.get("username"));
                    String password = req.get("password") == null ? null : String.valueOf(req.get("password"));

                    AuthStore.Session s = authStore.login(username, password);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", true,
                            "playerId", s.playerId,
                            "username", s.username,
                            "token", s.token)));
                } catch (Exception e) {
                    exchange.setStatusCode(401);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        authHandler.addExactPath("/me", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                try {
                    String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
                    AuthStore.Session s = authStore.getSessionFromAuthorizationHeader(auth);
                    if (s == null) {
                        exchange.setStatusCode(401);
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "unauthorized")));
                        return;
                    }
                    AuthStore.Account a = authStore.loadAccount(s.username);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", true,
                            "playerId", s.playerId,
                            "username", s.username,
                            "gameId", a == null ? "" : (a.gameId == null ? "" : a.gameId))));
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        authHandler.addExactPath("/logout", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
                    AuthStore.Session s = authStore.getSessionFromAuthorizationHeader(auth);
                    if (s != null) {
                        authStore.logout(s.token);
                    }
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of("ok", true)));
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        authHandler.addExactPath("/gameId", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String auth = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
                    AuthStore.Session s = authStore.getSessionFromAuthorizationHeader(auth);
                    if (s == null) {
                        exchange.setStatusCode(401);
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "unauthorized")));
                        return;
                    }

                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (body.isBlank()) {
                        body = "{}";
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body, Map.class);
                    String gameId = req.get("gameId") == null ? "" : String.valueOf(req.get("gameId"));

                    authStore.setGameId(s.playerId, gameId);
                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", true,
                            "playerId", s.playerId,
                            "gameId", gameId.trim())));
                } catch (IllegalArgumentException e) {
                    exchange.setStatusCode(400);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addPrefixPath("/auth", authHandler);

        // --- Mods API ---
        ModOrderRepository modOrderRepository = new ModOrderRepository();
        ModManager modManager = new ModManager(modOrderRepository);

        apiHandler.addExactPath("/mods", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                try {
                    ModOrder conf = modOrderRepository.load();
                    List<String> discovered = modManager.listAllModIdsDiscovered();
                    List<String> order = conf != null && conf.order != null ? conf.order : List.of();
                    Set<String> disabledSet = conf != null && conf.disabled != null ? conf.disabled : Set.of();

                    LinkedHashSet<String> merged = new LinkedHashSet<>();
                    for (String id : order) {
                        if (id != null && !id.isBlank()) {
                            merged.add(id.trim());
                        }
                    }
                    for (String id : discovered) {
                        if (id != null && !id.isBlank()) {
                            merged.add(id.trim());
                        }
                    }
                    ArrayList<String> mergedList = new ArrayList<>(merged);

                    ArrayList<Map<String, Object>> mods = new ArrayList<>();
                    for (int i = 0; i < mergedList.size(); i++) {
                        String id = mergedList.get(i);
                        boolean enabled = !disabledSet.contains(id);

                        ModMetadata meta = new ModMetadata();
                        File metaFile = new File("gamedata/mods/" + id + "/mod.json");
                        if (metaFile.exists() && metaFile.isFile()) {
                            try {
                                meta = objectMapper.readValue(metaFile, ModMetadata.class);
                            } catch (Exception ignored) {
                            }
                        }

                        Map<String, Object> modData = new TreeMap<>();
                        modData.put("id", id);
                        modData.put("enabled", enabled);
                        modData.put("orderIndex", i);
                        modData.put("name", meta.name);
                        modData.put("description", meta.description);
                        modData.put("version", meta.version);
                        modData.put("compatibleGameVersion", meta.compatibleGameVersion);
                        modData.put("author", meta.author);
                        mods.add(modData);
                    }

                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", true,
                            "mods", mods,
                            "order", mergedList,
                            "disabled", new ArrayList<>(disabledSet))));
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/mods/order", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod().toString())) {
                    exchange.setStatusCode(405);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", "method_not_allowed")));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                    return;
                }
                try {
                    exchange.startBlocking();
                    String body = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (body.isBlank()) {
                        body = "{}";
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = objectMapper.readValue(body, Map.class);

                    Object orderObj = req.get("order");
                    Object disabledObj = req.get("disabled");

                    ArrayList<String> newOrder = new ArrayList<>();
                    if (orderObj instanceof List) {
                        for (Object o : (List<?>) orderObj) {
                            if (o == null) {
                                continue;
                            }
                            String s = String.valueOf(o).trim();
                            if (!s.isBlank()) {
                                newOrder.add(s);
                            }
                        }
                    }

                    Set<String> newDisabled = new LinkedHashSet<>();
                    if (disabledObj instanceof List) {
                        for (Object o : (List<?>) disabledObj) {
                            if (o == null) {
                                continue;
                            }
                            String s = String.valueOf(o).trim();
                            if (!s.isBlank()) {
                                newDisabled.add(s);
                            }
                        }
                    }

                    File f = modOrderRepository.file();
                    Map<String, Object> root = new TreeMap<>();
                    if (f.exists() && f.isFile()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> old = objectMapper.readValue(f, Map.class);
                            if (old != null) {
                                root.putAll(old);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    root.put("schemaVersion", 1);
                    root.put("order", newOrder);
                    root.put("disabled", new ArrayList<>(newDisabled));

                    if (f.getParentFile() != null) {
                        f.getParentFile().mkdirs();
                    }
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(f, root);

                    exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                            "ok", true)));
                } catch (Exception e) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseSender().send(objectMapper.writeValueAsString(Map.of(
                                "ok", false,
                                "error", String.valueOf(e.getMessage()))));
                    } catch (Exception ignored) {
                        exchange.endExchange();
                    }
                }
            });
        });

        apiHandler.addExactPath("/status", exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            int c = connectionCount.get();
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

            String json = "{" +
                    "\"host\":\"" + config.host + "\"," +
                    "\"port\":" + config.port + "," +
                    "\"connections\":" + c + "," +
                    "\"autoExitSeconds\":" + config.autoExitSeconds + "," +
                    "\"idleSeconds\":" + (idleMs / 1000) + "," +
                    "\"webUiExists\":" + webUiExists + "," +
                    "\"webUiIndexExists\":" + webUiIndexExists + "," +
                    "\"webUiLastModifiedMs\":" + webUiLastModified + "," +
                    "\"webUiFileCount\":" + webUiFileCount + "," +
                    "\"webUiTotalBytes\":" + webUiTotalBytes +
                    "}";
            exchange.getResponseSender().send(json);
        });

        apiHandler.addExactPath("/ping", exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("{\"serverTimeMs\":" + System.currentTimeMillis() + "}");
        });

        apiHandler.addExactPath("/quit", exchange -> {
            System.out.println("HTTP quit requested: " + exchange.getRequestMethod() + " " + exchange.getRequestPath());
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("{\"ok\":true}");
            exchange.endExchange();
            Thread t = new Thread(() -> shutdownAndExit(0), "webnet-quit");
            t.setDaemon(false);
            t.start();
        });

        // --- i18n API ---
        PathHandler i18nHandler = Handlers.path();
        i18nHandler.addExactPath("/languages", exchange -> {
            List<String> languages = I18nApi.listAvailableLanguages();
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(objectMapper.writeValueAsString(languages));
        });
        i18nHandler.addPrefixPath("/", exchange -> {
            String lang = exchange.getRelativePath().substring(1);
            if (lang.contains("/") || lang.contains(".")) {
                exchange.setStatusCode(404).endExchange();
                return;
            }
            Map<String, String> strings = I18nApi.loadMergedStrings(lang);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(objectMapper.writeValueAsString(strings));
        });
        apiHandler.addPrefixPath("/i18n", i18nHandler);

        routes.addPrefixPath("/api", apiHandler);

        // --- Static Content ---
        // 游戏主界面（webui 目录）：挂在 /webui
        routes.addPrefixPath("/webui", createGameUiHandler());

        // 根路径统一跳转到 webui（不再提供 server-ui 页面）
        routes.addExactPath("/", exchange -> {
            exchange.setStatusCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, "/webui/");
            exchange.endExchange();
        });
        routes.addExactPath("/index.html", exchange -> {
            exchange.setStatusCode(302);
            exchange.getResponseHeaders().put(Headers.LOCATION, "/webui/");
            exchange.endExchange();
        });

        undertow = Undertow.builder().addHttpListener(config.port, config.host).setHandler(routes).build();
        undertow.start();

        System.out.println("WebNet HTTP listening on http://" + config.host + ":" + config.port);
        System.out.println("WebNet WS listening on ws://" + config.host + ":" + config.port + "/ws");
        System.out.println("WebNet status: http://" + config.host + ":" + config.port + "/api/status");

        startAutoExitWatcher();
    }

    private void startAutoExitWatcher() {
        int seconds = config.autoExitSeconds;
        if (seconds <= 0) {
            return;
        }
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (connectionCount.get() > 0) {
                    return;
                }
                long last = lastDisconnectAtMs.get();
                if (last <= 0) {
                    return;
                }
                long idleMs = System.currentTimeMillis() - last;
                if (idleMs >= seconds * 1000L) {
                    System.out.println("WebNet auto-exit: no connections for " + seconds + "s, shutting down.");
                    shutdownAndExit(0);
                }
            } catch (Exception ignored) {
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private HttpHandler createGameUiHandler() {
        File webUi = new File("webui");
        if (!webUi.exists() || !webUi.isDirectory()) {
            return exchange -> {
                exchange.setStatusCode(500);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
                exchange.getResponseSender().send(
                        "Web 前端未构建：未找到目录 ./webui\n" +
                                "请先将前端构建产物放入 webui/（例如 web 构建后复制 dist 到 webui），或检查工作目录/路径配置。\n");
            };
        }
        ResourceHandler rh = new ResourceHandler(new FileResourceManager(webUi, 1024 * 1024));
        rh.setWelcomeFiles("index.html");
        return rh;
    }

    public void stop() {
        if (undertow != null) {
            undertow.stop();
            undertow = null;
        }
    }

    private void shutdownAndExit(int code) {
        System.out.println("WebNet shutting down... closing ws channels=" + channels.size());
        for (WebSocketChannel ch : channels) {
            try {
                WebSockets.sendClose(1000, "bye", ch, null);
            } catch (Exception ignored) {
            }
            try {
                ch.close();
            } catch (Exception ignored) {
            }
        }
        channels.clear();
        try {
            stop();
        } catch (Exception ignored) {
        }
        try {
            gameTicker.shutdownNow();
        } catch (Exception ignored) {
        }
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
        System.out.println("WebNet exit(" + code + ")");
        System.exit(code);
    }
}
