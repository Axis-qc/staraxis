package staraxis.webnet.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.StarAxisGameRuntime;
import staraxis.webnet.core.GameLog;
import staraxis.webnet.game.GameSessions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebCommandRegistry
 *
 * @description
 *              游戏命令注册表，负责管理和路由前端通过 WebSocket 发送的游戏命令。
 *              提供模块化的命令注册、查询和统一处理机制，避免在 WebNetServer 中硬编码命令逻辑。
 *
 *              作用：
 *              - 维护命令类型到处理器的映射关系。
 *              - 提供统一的命令处理入口，包含 JSON 解析、错误处理和响应格式化。
 *              - 支持动态注册新命令，便于扩展游戏功能。
 *
 * @usage
 *        - 在 WebNetServer 构造函数中创建实例：
 *        - commandRegistry = new WebCommandRegistry(objectMapper);
 *        - 注册命令处理器：
 *        - commandRegistry.register(new SetSimTimeSpeedCommand());
 *        - 在 WebSocket 消息处理中使用：
 *        - if (commandRegistry.supports(type)) {
 *        String response = commandRegistry.handleTextMessage(text);
 *        WebSockets.sendText(response, channel, null);
 *        }
 *
 * @provides
 *           - **命令注册**: register(handler) 方法注册新的命令处理器。
 *           - **命令查询**: supports(type) 方法检查是否支持指定命令类型。
 *           - **命令处理**: handleTextMessage(text) 方法统一处理命令消息。
 *
 * @api
 *      - WebCommandRegistry(ObjectMapper objectMapper): 构造函数，需要 Jackson
 *      ObjectMapper 实例。
 *      - void register(WebCommandHandler handler): 注册命令处理器。
 *      - boolean supports(String type): 检查是否支持指定命令类型。
 *      - String handleTextMessage(String text): 处理 JSON 格式的命令消息，返回 JSON 响应。
 *
 * @important_notes
 *                  - 使用 ConcurrentHashMap 保证线程安全，支持多线程并发访问。
 *                  - 统一错误处理，所有异常都会返回标准格式的错误响应。
 *                  - 命令处理前会检查 GameSessions.getRuntime()，确保游戏运行时存在。
 *                  - JSON 解析失败、命令不存在、运行时不存在等错误都会记录日志。
 */
public class WebCommandRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, WebCommandHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * 
     * @param objectMapper Jackson ObjectMapper 实例，用于 JSON 序列化/反序列化
     */
    public WebCommandRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注册命令处理器
     * 
     * @param handler 要注册的命令处理器，不能为 null
     * @throws IllegalArgumentException 如果 handler 为 null 或 type 无效
     */
    public void register(WebCommandHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler_required");
        }
        String type = handler.type();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("handler_type_required");
        }
        handlers.put(type, handler);
    }

    /**
     * 检查是否支持指定命令类型
     * 
     * @param type 命令类型字符串
     * @return 如果支持返回 true，否则返回 false
     */
    public boolean supports(String type) {
        if (type == null)
            return false;
        return handlers.containsKey(type);
    }

    /**
     * 处理 JSON 格式的命令消息
     * 
     * @param text JSON 格式的命令消息字符串
     * @return JSON 格式的响应字符串，包含 type 和 ok 字段
     */
    public String handleTextMessage(String text) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(text, Map.class);
            Object typeObj = m.get("type");
            String type = typeObj == null ? null : String.valueOf(typeObj);

            if (type == null || type.isBlank()) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"missing_type\"}";
            }

            WebCommandHandler handler = handlers.get(type);
            if (handler == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"unsupported_command\"}";
            }

            StarAxisGameRuntime runtime = GameSessions.getRuntime();
            if (runtime == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"no_game_runtime\"}";
            }

            return handler.handle(m, runtime);
        } catch (Exception e) {
            try {
                GameLog.log("WebCommandRegistry.handleTextMessage failed: " + String.valueOf(e));
            } catch (Exception ignored) {
            }
            return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"command_failed\"}";
        }
    }
}
