package com.staraxis.game.shared.network;

import com.staraxis.game.shared.model.Command;

/**
 * 玩家指令消息包 (Client -> Server)
 */
public class PlayerCommandMessage {

    public Command command; // 指令内容

    public PlayerCommandMessage() {
    }
}
