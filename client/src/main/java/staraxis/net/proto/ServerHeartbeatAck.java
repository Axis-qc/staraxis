package staraxis.net.proto;

public class ServerHeartbeatAck {

    private final long clientSeq;
    private final long serverTick;

    public ServerHeartbeatAck(long clientSeq, long serverTick) {
        this.clientSeq = clientSeq;
        this.serverTick = serverTick;
    }

    public long getClientSeq() {
        return clientSeq;
    }

    public long getServerTick() {
        return serverTick;
    }
}
