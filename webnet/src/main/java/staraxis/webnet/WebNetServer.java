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

import staraxis.webnet.auth.AuthStore;
import staraxis.webnet.core.WebNetServerConfig;
import staraxis.webnet.core.WsConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.webnet.command.WebCommandRegistry;
import staraxis.webnet.command.SetSimTimeSpeedCommand;
import staraxis.webnet.command.SpawnColonyShipCommand;
import staraxis.webnet.command.ColonizePlanetWebCommand;
import staraxis.webnet.ai.WebAiWebSocketHandler;
import staraxis.webnet.ai.WebAiAutoStarter;
import staraxis.webnet.websocket.SnapshotBroadcaster;
import staraxis.webnet.websocket.WebPlayerWebSocketHandler;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class WebNetServer {

    private void tickAndBroadcastSnapshots() {
        // 每 30 秒执行一次连接存活检测与心跳（由 connMgr 统一负责）喵
        long now = System.currentTimeMillis();
        if (now - lastLowFreqWsCheckMs >= 30_000L) {
            lastLowFreqWsCheckMs = now;
            connMgr.sweepAndPing();
        }

        // 每 1 分钟打印一次连接详情到 webnet.log 便于排查挤号/僵尸连接喵
        if (now - lastLowFreqConnLogMs >= 60_000L) {
            lastLowFreqConnLogMs = now;
            connMgr.logActiveConnections();
        }

        // tick + 广播细节下沉到 SnapshotBroadcaster 喵
        snapshotBroadcaster.tickAndBroadcast();
    }

    private final WebNetServerConfig config;
    private Undertow undertow;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthStore authStore = new AuthStore(objectMapper);
    private final WebCommandRegistry commandRegistry = new WebCommandRegistry(objectMapper);

    private final WsConnectionManager connMgr = new WsConnectionManager();
    private final staraxis.webnet.websocket.SnapshotBroadcaster snapshotBroadcaster;
    private final WebPlayerWebSocketHandler playerWebSocketHandler;
    private final WebAiWebSocketHandler aiWebSocketHandler;

    private final AtomicLong lastTickCostMs = new AtomicLong(0);
    private volatile long lastLowFreqWsCheckMs = 0;
    private volatile long lastLowFreqConnLogMs = 0;

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
        commandRegistry.register(new SpawnColonyShipCommand());
        commandRegistry.register(new ColonizePlanetWebCommand());
        this.snapshotBroadcaster = new staraxis.webnet.websocket.SnapshotBroadcaster(objectMapper, connMgr,
                lastTickCostMs);
        this.playerWebSocketHandler = new WebPlayerWebSocketHandler(objectMapper, authStore, connMgr, commandRegistry,
                this.snapshotBroadcaster);
        this.aiWebSocketHandler = new WebAiWebSocketHandler(authStore, connMgr);
    }

    public void start() {
        // 进程启动时，统一初始化并截断双模块日志喵！
        staraxis.webnet.core.WebNetLog.initTruncate();
        // game 模块日志也在进程启动时截断喵！
        staraxis.game.log.GameLog.initTruncate();

        staraxis.webnet.core.WebNetLog.log("WebNetServer.start host=" + config.host + " port=" + config.port);
        staraxis.webnet.core.WebNetLog.log(
                "Logging Policy: [webnet.log] & [game.log] truncated on process start. Frequent logs throttled to 1min/entry喵.");
        staraxis.webnet.core.WebNetLog.log(
                "Connection Policy: Silent connect/disconnect. Only critical errors (heartbeat timeout, kick) will be logged喵.");

        if (config.autoExitSeconds > 0) {
            connMgr.getLastDisconnectAtMsRef().set(System.currentTimeMillis());
        }

        PathHandler routes = Handlers.path();
        gameTicker.scheduleAtFixedRate(this::tickAndBroadcastSnapshots, 0, 50, TimeUnit.MILLISECONDS);

        routes.addExactPath("/ws", Handlers.websocket(playerWebSocketHandler::onConnect));

        routes.addPrefixPath("/ws/ai", Handlers.websocket((exchange, channel) -> {
            // AI WS 的鉴权与 playerId 绑定在 WebAiWebSocketHandler.onConnect 内完成喵
            aiWebSocketHandler.onConnect(exchange, channel);

            // 关闭时若已绑定 playerId，则注销 AI 连接喵
            channel.addCloseTask(connMgr::unregisterAi);
        }));

        staraxis.webnet.core.AdminApi.AdminActions adminActions = this::shutdownAndRestart;

        PathHandler apiRoot = staraxis.webnet.api.ApiRoutes.createApiHandler(
                objectMapper,
                authStore,
                connMgr,
                config,
                connMgr.getPlayerCountRef(),
                connMgr.getAiCountRef(),
                connMgr.getLastDisconnectAtMsRef(),
                adminActions,
                () -> lastTickCostMs.get());

        routes.addPrefixPath("/api", apiRoot);
        staraxis.webnet.core.WebUiRoutes.register(routes);
        routes.addPrefixPath("/assets", new ResourceHandler(new FileResourceManager(new File("assets"), 1024 * 1024)));

        undertow = Undertow.builder().addHttpListener(config.port, config.host).setHandler(routes).build();
        undertow.start();

        staraxis.webnet.core.WebNetLog.log("WebNet started on http://" + config.host + ":" + config.port);

        // 如果开启了 AI 预启动，则立即触发喵
        if (config.aiPrestart) {
            staraxis.webnet.core.WebNetLog.log("AI Prestart enabled, triggering assistant...");
            WebAiAutoStarter.ensureAiStartedIfNeeded();

            // 异步探测 AI 是否就绪喵
            scheduler.schedule(() -> {
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress("127.0.0.1", 17891), 1000);
                    staraxis.webnet.core.WebNetLog.log("AI Preheat Check: Success (Port 17891 is listening) 喵!");
                } catch (Exception e) {
                    staraxis.webnet.core.WebNetLog.log("AI Preheat Check: Not ready yet or failed (Port 17891) 喵.");
                }
            }, 10, java.util.concurrent.TimeUnit.SECONDS);
        }

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
                    staraxis.webnet.core.WebNetLog.log("WebNet auto-exit: idle for " + seconds + "s, shutting down.");
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
        staraxis.webnet.core.WebNetLog.log("WebNet restart requested...");
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
            staraxis.webnet.core.WebNetLog.log("Failed to restart: " + e.getMessage());
        }
        shutdownAndExit(0);
    }

    private void shutdownAndExit(int code) {
        staraxis.webnet.core.WebNetLog.log("WebNet shutting down...");
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
