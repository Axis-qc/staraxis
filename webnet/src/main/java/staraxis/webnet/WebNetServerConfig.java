package staraxis.webnet;

public class WebNetServerConfig {
    public final String host;
    public final int port;
    public final int autoExitSeconds;

    public WebNetServerConfig(String host, int port, int autoExitSeconds) {
        this.host = host;
        this.port = port;
        this.autoExitSeconds = autoExitSeconds;
    }
}
