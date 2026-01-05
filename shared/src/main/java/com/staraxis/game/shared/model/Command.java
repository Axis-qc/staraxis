package com.staraxis.game.shared.model;

import java.util.Map;

/**
 * 玩家指令实体 (Player Action Intent)
 *
 * 使用的接口: 无 提供的接口: 供客户端发送行为请求，服务端进行校验与执行
 */
public class Command /* 玩家指令 */ {

    public long playerId /* 玩家ID */;
    public CommandType commandType /* 指令类型 */;
    public long targetId /* 目标ID */; // 可选
    public Map<String, Object> parameters /* 指令参数 */;
    public long timestamp /* 发起时间戳 */;
    public long sequenceNumber /* 指令序号 */; // 用于冲突裁决

    public Command() {
    }
}
