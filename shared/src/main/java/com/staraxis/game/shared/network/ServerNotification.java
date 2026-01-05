package com.staraxis.game.shared.network;

/**
 * 服务端异步通知 (Server -> Client)
 */
public class ServerNotification {

    public String type; // 通知类型 // INFO, WARNING, ERROR
    public String content; // 通知内容

    public ServerNotification() {
    }
}
