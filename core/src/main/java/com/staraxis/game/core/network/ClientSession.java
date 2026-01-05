package com.staraxis.game.core.network;

/**
 * 客户端会话 (Client Session)
 *
 * 使用的接口: 无 提供的接口: 维护单个连接的生命周期、心跳及重连状态
 */
public class ClientSession {

    public final long playerId; // 玩家ID
    public final String playerName; // 玩家名称
    public long lastHeartbeat; // 上次心跳时间
    public boolean connected; // 当前是否在线
    public long disconnectTime; // 断开连接时间

    public ClientSession(long playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.lastHeartbeat = System.currentTimeMillis();
        this.connected = true;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        this.connected = true;
    }
}
