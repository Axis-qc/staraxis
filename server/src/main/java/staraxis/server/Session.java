package staraxis.server;

import io.netty.channel.Channel;

public class Session {

    public enum ControlChannelState {
        Connected,
        Disconnected
    }

    public enum DataChannelState {
        Unbound,
        Bound,
        Disconnected
    }

    private final String clientId;

    private volatile ControlChannelState controlChannelState;
    private volatile DataChannelState dataChannelState;

    private volatile long lastHeartbeatAtMs;
    private volatile long lastServerTickSent;

    private final Channel controlChannel;
    private Channel dataChannel;

    public Session(String clientId, Channel controlChannel) {
        this.clientId = clientId;
        this.controlChannel = controlChannel;
        this.controlChannelState = ControlChannelState.Connected;
        this.dataChannelState = DataChannelState.Unbound;
        this.lastHeartbeatAtMs = 0;
        this.lastServerTickSent = 0;
    }

    public String getClientId() {
        return clientId;
    }

    public ControlChannelState getControlChannelState() {
        return controlChannelState;
    }

    public void setControlChannelState(ControlChannelState controlChannelState) {
        this.controlChannelState = controlChannelState;
    }

    public DataChannelState getDataChannelState() {
        return dataChannelState;
    }

    public void setDataChannelState(DataChannelState dataChannelState) {
        this.dataChannelState = dataChannelState;
    }

    public long getLastHeartbeatAtMs() {
        return lastHeartbeatAtMs;
    }

    public void setLastHeartbeatAtMs(long lastHeartbeatAtMs) {
        this.lastHeartbeatAtMs = lastHeartbeatAtMs;
    }

    public long getLastServerTickSent() {
        return lastServerTickSent;
    }

    public void setLastServerTickSent(long lastServerTickSent) {
        this.lastServerTickSent = lastServerTickSent;
    }

    public Channel getDataChannel() {
        return dataChannel;
    }

    public void setDataChannel(Channel dataChannel) {
        this.dataChannel = dataChannel;
        this.dataChannelState = DataChannelState.Bound;
    }
}
