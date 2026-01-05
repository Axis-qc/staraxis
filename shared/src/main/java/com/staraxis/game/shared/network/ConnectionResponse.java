package com.staraxis.game.shared.network;

/**
 * 连接响应 (Server -> Client)
 */
public class ConnectionResponse {

    public boolean success; // 是否成功
    public String message; // 消息说明
    public long assignedPlayerId; // 分配ID

    public ConnectionResponse() {
    }
}
