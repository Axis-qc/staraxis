package staraxis.net.proto;

public class ClientHeartbeat {

    private final long clientSeq;
    private final long clientTimeMs;

    public ClientHeartbeat(long clientSeq, long clientTimeMs) {
        this.clientSeq = clientSeq;
        this.clientTimeMs = clientTimeMs;
    }

    public long getClientSeq() {
        return clientSeq;
    }

    public long getClientTimeMs() {
        return clientTimeMs;
    }
}
