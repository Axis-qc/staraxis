package staraxis.net.proto;

public class DataHello {

    private final String clientId;

    public DataHello(String clientId) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }
}
