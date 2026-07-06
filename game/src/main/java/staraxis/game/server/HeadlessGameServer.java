package staraxis.game.server;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.util.ProgressCallback;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldType;

/**
 * HeadlessGameServer（头戴式游戏服务端）。
 *
 * 纯命令行启动的 game 逻辑层独立入口，不依赖任何图形/渲染库。
 * 类似 Minecraft Server 模式：生成世界 -> Tick 循环 -> 控制台交互。
 *
 * 后续可结合 webnet 模块作为多人联机服务器使用。
 *
 * 启动示例：
 *   ./gradlew :game:run --args="--seed=42 --stars=1000 --tps=20"
 *   或
 *   java -jar game.jar --seed=42 --stars=1000
 */
public class HeadlessGameServer {

    private final ServerConfig config;
    private StarAxisGameRuntime runtime;
    private TickLoop tickLoop;
    private Console console;

    public HeadlessGameServer(ServerConfig config) {
        this.config = config;
    }

    /**
     * 初始化并生成世界。
     */
    public void init() {
        System.out.println("[Server] StarAxis \u5934\u888b\u5f0f\u670d\u52a1\u7aef v1.0");
        System.out.println("[Server] \u914d\u7f6e: \u79cd\u5b50=" + (config.worldSeed != null ? config.worldSeed : "(hashCode)")
            + " \u2502 \u6052\u661f\u7cfb=" + config.starCount
            + " \u2502 TPS=" + config.ticksPerSecond);

        WorldGenConfig genConfig = new WorldGenConfig();
        genConfig.worldSeed = config.worldSeed;
        genConfig.systemCount = config.starCount;
        genConfig.worldType = WorldType.SINGLE_PLAYER;

        System.out.println("[Server] \u6b63\u5728\u751f\u6210\u4e16\u754c...");
        long startTime = System.currentTimeMillis();

        runtime = StarAxisGameRuntime.newGame(genConfig, new ProgressCallback() {
            @Override
            public void onProgress(float progress, String phase) {
                if (config.verbose && phase != null) {
                    System.out.printf("[Server] \u751f\u6210\u8fdb\u5ea6: %.0f%% - %s%n", progress * 100, phase);
                }
            }
        });

        long elapsed = System.currentTimeMillis() - startTime;
        var ws = runtime.getWorldStateForSimOnly();
        int systems = ws.astro.getSystemsView().size();
        int entities = ws.entitiesById.size();
        System.out.println("[Server] \u4e16\u754c\u751f\u6210\u5b8c\u6210 \u2502 \u8017\u65f6=" + elapsed + "ms"
            + " \u2502 \u6052\u661f\u7cfb=" + systems + " \u2502 \u5b9e\u4f53=" + entities);

        runtime.start();

        // 初始化 Tick 循环
        tickLoop = new TickLoop(runtime, config.ticksPerSecond);

        // 初始化控制台
        console = new Console(runtime, tickLoop, config);
    }

    /**
     * 启动服务。
     */
    public void run() {
        if (config.autoStart) {
            // 启动控制台线程（独立线程读取 stdin）
            Thread consoleThread = new Thread(() -> console.start(), "server-console");
            consoleThread.setDaemon(true);
            consoleThread.start();

            // 主线程运行 Tick 循环
            tickLoop.start();
        } else {
            // 仅启动控制台，等待命令
            console.start();
        }
    }

    public static void main(String[] args) {
        // --help 时打印帮助并退出
        if (args.length == 1 && args[0].equals("--help")) {
            ServerConfig.printHelp();
            return;
        }

        ServerConfig config = ServerConfig.parse(args);
        HeadlessGameServer server = new HeadlessGameServer(config);
        server.init();
        server.run();
    }
}
