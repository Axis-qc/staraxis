package staraxis.webnet;

/**
 * WebNetServerConfig
 *
 * 作用：
 * - WebNetServer 的运行配置载体（监听 host/port、自动退出策略等）。
 *
 * 字段说明：
 * - host：HTTP/WS 监听地址（默认 127.0.0.1）。
 * - port：HTTP/WS 监听端口（默认 17890）。
 * - autoExitSeconds：当连接数归零后，空闲达到该秒数自动退出进程（<=0 表示禁用）。
 *
 * 兼容字段（历史遗留）：
 * - serverUiEnabled / gameUiUrl：早期用于 server-ui 启动台跳转。
 * 当前已不再提供 server-ui，但保留字段以避免破坏旧启动参数/构造方法。
 */

public class WebNetServerConfig {
    public final String host;
    public final int port;
    public final int autoExitSeconds;

    public final boolean serverUiEnabled;
    public final String gameUiUrl;

    public WebNetServerConfig(String host, int port, int autoExitSeconds) {
        this(host, port, autoExitSeconds, true, "http://127.0.0.1:5173/");
    }

    public WebNetServerConfig(String host, int port, int autoExitSeconds,
            boolean serverUiEnabled, String gameUiUrl) {
        this.host = host;
        this.port = port;
        this.autoExitSeconds = autoExitSeconds;
        this.serverUiEnabled = serverUiEnabled;
        this.gameUiUrl = gameUiUrl;
    }
}
