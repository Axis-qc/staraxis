package staraxis.net.proto;

public class ClientHello {

    private final String clientVersion;

    public ClientHello(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getClientVersion() {
        return clientVersion;
    }
}
