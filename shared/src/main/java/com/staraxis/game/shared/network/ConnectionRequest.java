package com.staraxis.game.shared.network;

/**
 * 连接请求 (Client -> Server)
 */
public class ConnectionRequest {

    public String playerName; // 玩家名称
    public String version; // 版本号

    public ConnectionRequest() {
    }
}
