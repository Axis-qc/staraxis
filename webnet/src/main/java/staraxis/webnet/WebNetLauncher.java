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

import java.awt.Desktop;
import java.net.URI;

public class WebNetLauncher {

    public static void main(String[] args) {
        String host = getArgOrDefault(args, "--host", "127.0.0.1");
        int port = parseIntOrDefault(getArgOrDefault(args, "--port", null), 17890);
        int autoExitSeconds = parseIntOrDefault(getArgOrDefault(args, "--autoExitSeconds", null), 60);

        boolean serverUiEnabled = "true".equalsIgnoreCase(getArgOrDefault(args, "--serverUi", "true"));
        String gameUiUrl = getArgOrDefault(args, "--gameUiUrl", "http://127.0.0.1:5173/");

        WebNetServerConfig cfg = new WebNetServerConfig(host, port, autoExitSeconds, serverUiEnabled, gameUiUrl);
        WebNetServer server = new WebNetServer(cfg);
        server.start();

        String url = "http://" + cfg.host + ":" + cfg.port + "/webui/";
        System.out.println("WebNet started: " + url);

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
}
