package staraxis.webnet;

/**
 * WebNetLauncher
 *
 * 作用：
 * - webnet 模块的启动入口（main 方法）。
 * - 负责解析启动参数（host/port/autoExitSeconds 等），创建 WebNetServer 并启动。
 * - 启动后尝试自动打开浏览器，访问 /webui/。
 *
 * 参数：
 * - --host=127.0.0.1         监听地址
 * - --port=17890             监听端口
 * - --autoExitSeconds=60     无 WS 连接空闲自动退出秒数（<=0 关闭）
 */

import staraxis.webnet.core.WebNetServerConfig;
import java.awt.Desktop;
import java.net.URI;
import java.io.File;
import java.io.FileInputStream;

public class WebNetLauncher {

    public static void main(String[] args) {
        java.util.Properties props = loadWebNetProperties();

        String host = getArgOrDefault(args, "--host", props.getProperty("host", "127.0.0.1"));
        int port = parseIntOrDefault(getArgOrDefault(args, "--port", null),
                parseIntOrDefault(props.getProperty("port"), 17890));
        int autoExitSeconds = parseIntOrDefault(getArgOrDefault(args, "--autoExitSeconds", null),
                parseIntOrDefault(props.getProperty("autoExitSeconds"), 60));
        boolean aiPrestart = "true".equalsIgnoreCase(
                getArgOrDefault(args, "--aiPrestart", props.getProperty("aiPrestart", "true")));

        boolean serverUiEnabled = "true".equalsIgnoreCase(
                getArgOrDefault(args, "--serverUi", props.getProperty("serverUi", "true")));
        String gameUiUrl = getArgOrDefault(args, "--gameUiUrl",
                props.getProperty("gameUiUrl", "http://127.0.0.1:5173/"));

        WebNetServerConfig cfg = new WebNetServerConfig(host, port, autoExitSeconds, aiPrestart, serverUiEnabled,
                gameUiUrl);
        WebNetServer server = new WebNetServer(cfg);
        server.start();

        String url = "http://" + cfg.host + ":" + cfg.port + "/webui/";
        staraxis.webnet.core.WebNetLog.log("WebNet started: " + url);

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
        }
    }

    private static String getArgOrDefault(String[] args, String key, String def) {
        if (args == null || args.length == 0) {
            return def;
        }
        for (int i = 0; i < args.length; i++) {
            if (key.equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
            if (args[i] != null && args[i].startsWith(key + "=")) {
                return args[i].substring((key + "=").length());
            }
        }
        return def;
    }

    private static int parseIntOrDefault(String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 加载 webnet 配置文件喵。
     * 优先级由调用方保证：命令行参数 > 配置文件 > 代码默认值喵。
     *
     * 默认路径：webnet/config/webnet.properties（相对进程工作目录）喵。
     */
    private static java.util.Properties loadWebNetProperties() {
        java.util.Properties props = new java.util.Properties();
        File f = new File("webnet/config/webnet.properties");
        if (!f.exists() || !f.isFile()) {
            return props;
        }
        try (FileInputStream fis = new FileInputStream(f)) {
            props.load(fis);
        } catch (Exception e) {
            staraxis.webnet.core.WebNetLog.log("Failed to load webnet.properties: " + e.getMessage());
        }
        return props;
    }
}
