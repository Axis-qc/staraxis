package com.staraxis.game.client.input;

import com.staraxis.game.client.GameClient;
import com.staraxis.game.shared.model.Command;
import com.staraxis.game.shared.model.CommandType;

/**
 * 输入转发器 (Input Forwarder)
 *
 * 使用的接口: GameClient, Command 提供的接口: 监听本地输入并将其封装为服务端指令发送
 */
public class InputForwarder {

    private final GameClient client;
    private final long playerId;

    public InputForwarder(GameClient client, long playerId) {
        this.client = client;
        this.playerId = playerId;
    }

    /**
     * 模拟发送移动请求 (Simulate MOVE command)
     */
    public void requestMove(float targetX, float targetY) {
        Command moveCmd = new Command();
        moveCmd.playerId = playerId;
        moveCmd.commandType = CommandType.MOVE;
        moveCmd.timestamp = System.currentTimeMillis();
        // TODO: 设置坐标参数

        client.sendCommand(moveCmd);
    }
}
