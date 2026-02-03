package staraxis.webnet.command;

import staraxis.game.StarAxisGameRuntime;

import java.util.Map;

/**
 * WebCommandHandler
 *
 * @description
 *              游戏命令处理器接口，用于模块化处理前端通过 WebSocket 发送的游戏命令。
 *
 *              作用：
 *              - 为每种命令类型提供独立的处理逻辑，避免在 WebNetServer 中硬编码命令分支。
 *              - 统一命令处理签名，便于在 WebCommandRegistry 中注册和调用。
 *
 * @usage
 *        - 实现 WebCommandHandler 接口：
 *        - type(): 返回命令类型字符串（如 "setSimTimeSpeed"）。
 *        - handle(): 处理命令消息并返回 JSON 格式的响应字符串。
 *        - 在 WebNetServer 构造函数中注册：
 *        - commandRegistry.register(new YourCommandHandler());
 *
 * @provides
 *           - **命令类型标识**: type() 方法返回的命令类型字符串。
 *           - **命令处理逻辑**: handle() 方法实现具体的命令处理逻辑。
 *
 * @api
 *      - String type(): 返回命令类型（必须与前端发送的 type 字段匹配）。
 *      - String handle(Map<String, Object> message, StarAxisGameRuntime
 *      runtime):
 *      - message: 解析后的命令消息 Map。
 *      - runtime: 当前游戏运行时实例，可直接操作游戏状态。
 *      - 返回: JSON 格式的响应字符串，格式参考：
 *      - 成功: {"type":"command_response","ok":true,"command":"xxx",...}
 *      - 失败: {"type":"command_response","ok":false,"error":"error_message"}
 *
 * @important_notes
 *                  - 命令处理逻辑应该是幂等的，重复执行相同命令不应产生副作用。
 *                  - 返回的 JSON 必须包含 type 和 ok 字段，便于前端统一处理。
 *                  - 错误信息应该简洁明确，便于前端展示给用户。
 *                  - 避免在 handle() 方法中执行耗时操作，以免阻塞 WebSocket 线程。
 */
public interface WebCommandHandler {

    /**
     * 返回命令类型标识符
     * 
     * @return 命令类型字符串，必须与前端发送的 type 字段完全匹配
     */
    String type();

    /**
     * 处理命令消息
     * 
     * @param message 解析后的命令消息 Map，包含所有前端发送的字段
     * @param runtime 当前游戏运行时实例，可直接操作游戏状态
     * @return JSON 格式的响应字符串，必须包含 type 和 ok 字段
     */
    String handle(Map<String, Object> message, StarAxisGameRuntime runtime);
}
