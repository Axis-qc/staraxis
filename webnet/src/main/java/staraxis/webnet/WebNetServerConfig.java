package staraxis.webnet;

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
