package staraxis.net.proto;

public class ServerHello {

    private final String clientId;
    private final long serverTick;

    public ServerHello(String clientId, long serverTick) {
        this.clientId = clientId;
        this.serverTick = serverTick;
    }

    public String getClientId() {
        return clientId;
    }

    public long getServerTick() {
        return serverTick;
    }
}
