package staraxis.webnet;

/**
 * WebNetServer
 *
 * 作用：
 * - StarAxis Web 版本的本地权威服务端入口（HTTP + WebSocket）。
 * - 本类负责 Undertow 服务器生命周期（start/stop）、路由组装与 WS 通道接入。
 * - 具体业务 API（认证、Mods 管理、管理接口、WebUI 静态托管等）已下沉到对应模块，由本类进行挂载。
 *
 * 职责边界：
 * - 负责：Undertow 启停与顶层 PathHandler 组装；/ws（玩家）与 /ws/ai（AI）WebSocket 端点挂载；tick 驱动并广播快照。
 * - 不负责：认证/账号具体实现（AuthApi（认证HTTP路由处理）/AuthStore（会话与账号存储））；Mods 管理（ModsApi（mods路由处理）/ModManager（顺序口径））；管理接口（AdminApi（status/ping/quit/restart））；WebUI 静态资源（WebUiRoutes（/webui 与根路径跳转））。
 *
 * 路由概览：
 * - WebSocket：
 *   - GET /ws（玩家 WS，承载订阅快照与命令消息）
 *   - GET /ws/ai（AI WS，工具调用与握手协议）
 *
 * - 静态资源：
 *   - GET /webui/**（由 WebUiRoutes（WebUI静态路由）托管项目根目录 webui/）
 *   - GET /        （由 WebUiRoutes 302 跳转到 /webui/）
 *   - GET /assets/**（静态资源直出，供前端加载）
 *
 * - API（均在 /api 前缀下）：
 *   - AdminApi（管理接口）：
 *     - GET  /api/status（服务状态）
 *     - GET  /api/ping（连通性测试）
 *     - POST /api/quit（退出进程，需 ADMIN 权限）
 *     - POST /api/restart（重启进程，需 ADMIN 权限）
 *   - AuthApi（认证接口）：/api/auth/**
 *   - ModsApi（Mods 管理）：/api/mods/**
 *   - I18nApi（语言包合并）：/api/i18n/**
 *   - ShipApi（舰船编辑工具）：/api/ship/**
 *   - 其它业务域 API：/api/newgame/**、/api/nation/** 等
 *
 * 重要注意事项（Undertow 阻塞 IO）：
 * - Undertow 的请求处理默认运行在 IO 线程中。
 * - 读取请求体（startBlocking/getInputStream）、文件读写（gamedata/**）、以及 JSON 序列化等都可能触发阻塞 IO。
 * - 如果在 IO 线程里做阻塞操作，会触发 UT000126 并导致请求 500。
 * - 因此涉及阻塞 IO 的 handler 必须使用 exchange.dispatch(...) 切换到 worker 线程处理。
 */

import staraxis.webnet.api.I18nApi;
import staraxis.webnet.api.ShipApi;
import staraxis.webnet.auth.AuthApi;
import staraxis.webnet.auth.AuthStore;
import staraxis.webnet.core.GameLog;
import staraxis.webnet.core.WebNetServerConfig;
import staraxis.webnet.core.WsConnectionManager;
import staraxis.webnet.mod.ModManager;
import staraxis.webnet.mod.ModOrderRepository;
import staraxis.webnet.mod.ModsApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.webnet.game.GameSessions;
import staraxis.webnet.websocket.SnapshotMessageFactory;
import staraxis.webnet.command.WebCommandRegistry;
import staraxis.webnet.command.SetSimTimeSpeedCommand;
import staraxis.webnet.ai.AiConfigApi;
import staraxis.webnet.ai.AiChatApi;
import staraxis.webnet.ai.AiUsageApi;
import staraxis.webnet.ai.WebAiWebSocketHandler;
import staraxis.webnet.ai.WebAiAutoStarter;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class WebNetServer {

    private void tickAndBroadcastSnapshots() {
        // 每 1 分钟执行一次连接存活检测与心跳（由 connMgr 统一负责）喵
        long now = System.currentTimeMillis();
        if (now - lastLowFreqWsCheckMs >= 60_000L) {
            lastLowFreqWsCheckMs = now;
            connMgr.sweepAndPing();
        }

        staraxis.game.StarAxisGameRuntime runtime = staraxis.webnet.game.GameSessions.getRuntime();
        if (runtime == null) {
            return;
        }

        long t0 = System.nanoTime();
        try {
            runtime.update(0f);
        } catch (Exception e) {
            return;
        } finally {
            long costMs = Math.max(0, (System.nanoTime() - t0) / 1_000_000L);
            lastTickCostMs.set(costMs);
        }

        try {
            var snapshotDto = SnapshotMessageFactory.buildSnapshotMessage(runtime, lastTickCostMs.get());

            Set<WebSocketChannel> snapshotSubscribers = connMgr.getSnapshotSubscribers();
            if (snapshotSubscribers.isEmpty()) {
                return;
            }

            String json = objectMapper.writeValueAsString(snapshotDto);
            for (WebSocketChannel ch : snapshotSubscribers) {
                if (ch != null && ch.isOpen()) {
                    WebSockets.sendText(json, ch, null);
                }
            }
        } catch (Exception e) {
            try {
                GameLog.log("tickAndBroadcastSnapshots snapshot_build_failed: " + String.valueOf(e));
            } catch (Exception ignored) {
            }
        }
    }

    private void sendSnapshotToChannel(WebSocketChannel channel) {
        if (channel == null || !channel.isOpen()) {
            return;
        }

        StarAxisGameRuntime runtime = GameSessions.getRuntime();
        if (runtime == null) {
            try {
                WebSockets.sendText(
                        objectMapper.writeValueAsString(SnapshotMessageFactory.buildWorldNotCreatedMessage()),
                        channel, null);
            } catch (Exception e) {
                WebSockets.sendText("{\"type\":\"snapshot\",\"ok\":false,\"error\":\"world_not_created\"}",
                        channel, null);
            }
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(
                    SnapshotMessageFactory.buildSnapshotMessage(runtime, lastTickCostMs.get()));
            WebSockets.sendText(json, channel, null);
        } catch (Exception e) {
            try {
                GameLog.log("sendSnapshotToChannel snapshot_build_failed: " + String.valueOf(e));
            } catch (Exception ignored) {
            }
            WebSockets.sendText("{\"type\":\"snapshot\",\"ok\":false,\"error\":\"snapshot_build_failed\"}",
                    channel, null);
        }
    }

    private final WebNetServerConfig config;
    private Undertow undertow;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthStore authStore = new AuthStore(objectMapper);
    private final WebCommandRegistry commandRegistry = new WebCommandRegistry(objectMapper);

    private final WsConnectionManager connMgr = new WsConnectionManager();
    private final WebAiWebSocketHandler aiWebSocketHandler;

    private final AtomicLong lastTickCostMs = new AtomicLong(0);
    private volatile int lastLoggedGameDay = -1;
    private volatile long lastLowFreqWsCheckMs = 0;

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

    public WebNetServer(WebNetServerConfig config) {
        this.config = config;
        commandRegistry.register(new SetSimTimeSpeedCommand());
        this.aiWebSocketHandler = new WebAiWebSocketHandler(authStore);
    }

    public void start() {
        GameLog.initTruncate();
        GameLog.log("WebNetServer.start host=" + config.host + " port=" + config.port);

        if (config.autoExitSeconds > 0) {
            connMgr.getLastDisconnectAtMsRef().set(System.currentTimeMillis());
        }

        PathHandler routes = Handlers.path();
        gameTicker.scheduleAtFixedRate(this::tickAndBroadcastSnapshots, 0, 40, TimeUnit.MILLISECONDS);

        routes.addExactPath("/ws", Handlers.websocket((WebSocketHttpExchange exchange, WebSocketChannel channel) -> {
            List<String> tokenParams = exchange.getRequestParameters().get("token");
            String token = (tokenParams == null || tokenParams.isEmpty()) ? null : tokenParams.get(0);

            AuthStore.Session session = (token == null) ? null : authStore.getSessionByToken(token);
            if (session == null) {
                try {
                    WebSockets.sendText(objectMapper.writeValueAsString(Map.of(
                            "type", "hello",
                            "ok", false,
                            "error", "unauthorized")), channel, null);
                    channel.sendClose();
                } catch (Exception ignored) {
                }
                return;
            }

            String playerId = session.playerId;
            channel.setIdleTimeout(60_000L);
            connMgr.registerPlayer(playerId, channel);

            channel.getReceiveSetter().set(new AbstractReceiveListener() {
                @Override
                protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                    String text = message.getData();
                    WebAiAutoStarter.reportActivity();

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = objectMapper.readValue(text, Map.class);
                        Object typeObj = m.get("type");
                        String type = typeObj == null ? null : String.valueOf(typeObj);

                        if ("subscribeSnapshot".equals(type)) {
                            connMgr.subscribeSnapshot(channel);
                            if (!GameSessions.hasRuntime()) {
                                WebSockets.sendText(
                                        "{\"type\":\"snapshot\",\"ok\":false,\"error\":\"world_not_created\"}", channel,
                                        null);
                            } else {
                                sendSnapshotToChannel(channel);
                            }
                            return;
                        }

                        if ("unsubscribeSnapshot".equals(type)) {
                            connMgr.unsubscribeSnapshot(channel);
                            WebSockets.sendText("{\"type\":\"unsubscribed\",\"ok\":true}", channel, null);
                            return;
                        }

                        if ("pong".equals(type)) {
                            connMgr.onPlayerPong(channel);
                            return;
                        }

                        if (commandRegistry.supports(type)) {
                            String response = commandRegistry.handleTextMessage(text);
                            WebSockets.sendText(response, channel, null);
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                    WebSockets.sendText(text, channel, null);
                }

                @Override
                protected void onClose(WebSocketChannel webSocketChannel,
                        io.undertow.websockets.core.StreamSourceFrameChannel frameChannel) {
                    connMgr.unregisterPlayer(webSocketChannel);
                }
            });

            channel.resumeReceives();
            WebSockets.sendText(
                    "{\"type\":\"hello\",\"ok\":true,\"server\":\"webnet\",\"playerId\":\"" + playerId + "\"}", channel,
                    null);
        }));

        routes.addPrefixPath("/ws/ai", Handlers.websocket((exchange, channel) -> {
            connMgr.registerAi(channel);
            channel.addCloseTask(connMgr::unregisterAi);
            aiWebSocketHandler.onConnect(exchange, channel);
        }));

        PathHandler apiHandler = Handlers.path();

        apiHandler.addExactPath("/game/nations", exchange -> {
            exchange.dispatch(() -> {
                try {
                    staraxis.webnet.api.nation.NationPresetsApi.setJsonContentType(exchange);
                    List<staraxis.game.nation.NationDef> nations = staraxis.webnet.api.nation.NationPresetsApi
                            .loadAllPresetNations(objectMapper);
                    exchange.getResponseSender().send(objectMapper
                            .writeValueAsString(staraxis.webnet.api.nation.NationPresetsApi.toResponse(nations)));
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
                    staraxis.webnet.api.nation.PlayerNationApi.setJsonContentType(exchange);
                    String username = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "username");
                    String playerId = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "playerId");
                    String json = staraxis.webnet.api.nation.PlayerNationApi.handleList(objectMapper,
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
                    staraxis.webnet.api.nation.PlayerNationApi.setJsonContentType(exchange);
                    String username = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "username");
                    String playerId = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "playerId");
                    String nationId = staraxis.webnet.api.nation.PlayerNationApi.query(exchange, "nationId");
                    String json = staraxis.webnet.api.nation.PlayerNationApi.handleGet(objectMapper,
                            new staraxis.webnet.repo.nation.PlayerNationFileRepository(objectMapper), username,
                            playerId, nationId);
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
                staraxis.webnet.api.nation.PlayerNationApi.setJsonContentType(exchange);
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
                    String json = staraxis.webnet.api.nation.PlayerNationApi.handleSave(objectMapper,
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

        apiHandler.addExactPath("/newgame/step1/selectNation", exchange -> {
            exchange.dispatch(() -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                        staraxis.webnet.api.newgame.NewGameApi.jsonContentType());
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
                    Map<String, Object> req = staraxis.webnet.api.newgame.NewGameApi.parseBodyToMap(objectMapper, body);
                    staraxis.webnet.api.newgame.NewGameDraftRepository repo = new staraxis.webnet.api.newgame.NewGameDraftRepository(
                            objectMapper);
                    Map<String, Object> resp = staraxis.webnet.api.newgame.NewGameApi.step1SelectNation(objectMapper,
                            repo, req);
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
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                        staraxis.webnet.api.newgame.NewGameApi.jsonContentType());
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
                    Map<String, Object> req = staraxis.webnet.api.newgame.NewGameApi.parseBodyToMap(objectMapper, body);
                    staraxis.webnet.api.newgame.NewGameDraftRepository repo = new staraxis.webnet.api.newgame.NewGameDraftRepository(
                            objectMapper);
                    Map<String, Object> resp = staraxis.webnet.api.newgame.NewGameApi.step2WorldSettings(objectMapper,
                            repo, req);
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
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                        staraxis.webnet.api.newgame.NewGameApi.jsonContentType());
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
                    Map<String, Object> req = staraxis.webnet.api.newgame.NewGameApi.parseBodyToMap(objectMapper, body);
                    staraxis.webnet.api.newgame.NewGameDraftRepository repo = new staraxis.webnet.api.newgame.NewGameDraftRepository(
                            objectMapper);
                    Map<String, Object> resp = staraxis.webnet.api.newgame.NewGameApi.step3Confirm(objectMapper, repo,
                            req);
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

        AuthApi authApi = new AuthApi(authStore, objectMapper);
        apiHandler.addPrefixPath("/auth", authApi.createHandler());

        ModOrderRepository modOrderRepository = new ModOrderRepository();
        ModManager modManager = new ModManager(modOrderRepository);
        ModsApi modsApi = new ModsApi(objectMapper, modOrderRepository, modManager);
        apiHandler.addPrefixPath("/mods", modsApi.createHandler());

        staraxis.webnet.core.AdminApi adminApi = new staraxis.webnet.core.AdminApi(
                config, authStore, objectMapper,
                connMgr.getPlayerCountRef(),
                connMgr.getAiCountRef(),
                connMgr.getLastDisconnectAtMsRef(),
                this::shutdownAndRestart);
        apiHandler.addPrefixPath("/", adminApi.createHandler());

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

        ShipApi shipApi = new ShipApi(objectMapper);
        apiHandler.addPrefixPath("/ship", shipApi.createHandler());

        AiConfigApi aiConfigApi = new AiConfigApi(objectMapper);
        apiHandler.addPrefixPath("/ai/config", aiConfigApi.createHandler());

        AiChatApi aiChatApi = new AiChatApi(objectMapper, authStore);
        apiHandler.addPrefixPath("/ai/chat", aiChatApi.createHandler());

        AiUsageApi aiUsageApi = new AiUsageApi(objectMapper);
        apiHandler.addPrefixPath("/ai/usage", aiUsageApi.createHandler());

        HttpHandler apiWrapped = exchange -> {
            try {
                String rp = exchange.getRequestPath();
                if (rp != null && rp.startsWith("/api/") && !"/api/status".equals(rp)) {
                    WebAiAutoStarter.ensureAiStartedIfNeeded();
                    WebAiAutoStarter.reportActivity();
                }
            } catch (Exception ignored) {
            }
            apiHandler.handleRequest(exchange);
        };

        routes.addPrefixPath("/api", apiWrapped);
        staraxis.webnet.core.WebUiRoutes.register(routes);
        routes.addPrefixPath("/assets", new ResourceHandler(new FileResourceManager(new File("assets"), 1024 * 1024)));

        undertow = Undertow.builder().addHttpListener(config.port, config.host).setHandler(routes).build();
        undertow.start();

        System.out.println("WebNet started on http://" + config.host + ":" + config.port);
        startAutoExitWatcher();
    }

    private void startAutoExitWatcher() {
        int seconds = config.autoExitSeconds;
        if (seconds <= 0)
            return;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (connMgr.getPlayerCount() > 0)
                    return;
                long last = connMgr.getLastDisconnectAtMs();
                if (last <= 0)
                    return;
                if (System.currentTimeMillis() - last >= seconds * 1000L) {
                    System.out.println("WebNet auto-exit: idle for " + seconds + "s, shutting down.");
                    shutdownAndExit(0);
                }
            } catch (Exception ignored) {
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        if (undertow != null) {
            undertow.stop();
            undertow = null;
        }
    }

    private void shutdownAndRestart() {
        System.out.println("WebNet restart requested...");
        stop();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }

        String javaHome = System.getProperty("java.home");
        String javaExecutable = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-cp");
        command.add(classpath);
        command.add("staraxis.webnet.WebNetLauncher");
        command.add("--host=" + config.host);
        command.add("--port=" + config.port);
        command.add("--autoExitSeconds=" + config.autoExitSeconds);
        if (config.serverUiEnabled)
            command.add("--serverUi=true");
        if (config.gameUiUrl != null && !config.gameUiUrl.isBlank())
            command.add("--gameUiUrl=" + config.gameUiUrl);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(System.getProperty("user.dir")));
            pb.inheritIO();
            pb.start();
        } catch (Exception e) {
            System.err.println("Failed to restart: " + e.getMessage());
        }
        shutdownAndExit(0);
    }

    private void shutdownAndExit(int code) {
        System.out.println("WebNet shutting down...");
        for (WebSocketChannel ch : connMgr.getAllChannels()) {
            try {
                WebSockets.sendClose(1000, "bye", ch, null);
                ch.close();
            } catch (Exception ignored) {
            }
        }
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
        System.exit(code);
    }
}
