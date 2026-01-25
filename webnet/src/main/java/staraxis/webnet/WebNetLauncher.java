package staraxis.webnet;

import java.awt.Desktop;
import java.net.URI;

public class WebNetLauncher {

    public static void main(String[] args) {
        String host = getArgOrDefault(args, "--host", "127.0.0.1");
        int port = parseIntOrDefault(getArgOrDefault(args, "--port", null), 17890);
        int autoExitSeconds = parseIntOrDefault(getArgOrDefault(args, "--autoExitSeconds", null), 60);

        WebNetServerConfig cfg = new WebNetServerConfig(host, port, autoExitSeconds);
        WebNetServer server = new WebNetServer(cfg);
        server.start();

        String url = "http://" + cfg.host + ":" + cfg.port + "/";
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
