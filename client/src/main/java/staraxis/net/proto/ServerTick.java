package staraxis.net.proto;

public class ServerTick {

    private final long serverTick;

    public ServerTick(long serverTick) {
        this.serverTick = serverTick;
    }

    public long getServerTick() {
        return serverTick;
    }
}
