package staraxis.webnet.command;

import staraxis.game.StarAxisGameRuntime;
import staraxis.webnet.GameLog;

import java.util.Map;

/**
 * SetSimTimeSpeedCommand
 *
 * @description
 *              设置模拟时间倍率（timeScale）的命令处理器。
 *
 *              作用：
 *              - 接收前端通过 WebSocket 发送的 `setSimTimeSpeed` 命令。
 *              - 将命令提交到 game 层的命令队列，在下一个 tick 的 PrepareTick 阶段统一执行。
 *
 * @usage
 *        - 前端发送 JSON：
 *        - {"type":"setSimTimeSpeed","scale":2.0}
 *        - WebNetServer 中注册：
 *        - commandRegistry.register(new SetSimTimeSpeedCommand());
 *
 * @provides
 *           - **命令类型**: type() = "setSimTimeSpeed"
 *           - **命令提交**: 提交 SetTimeScaleCommand 到 game 层的命令队列
 *
 * @api
 *      - 入参字段：
 *      - scale: number（double），建议范围 0.25/0.5/0.75/1/2/3/4（前端档位）
 *      - 返回：统一 command_response：
 *      - ok=true:
 *      {"type":"command_response","ok":true,"command":"setSimTimeSpeed","scale":...}
 *      - ok=false: {"type":"command_response","ok":false,"error":"..."}
 *
 * @important_notes
 *                  - 该命令通过 game 层的命令队列执行，在下一个 tick 的 PrepareTick 阶段统一生效。
 *                  - 当前做了简单的范围校验（0.1~10.0），避免异常值导致模拟不稳定。
 *                  - 命令执行后会影响后续 tick 的 dtGameHours 计算。
 */
public class SetSimTimeSpeedCommand implements WebCommandHandler {

    @Override
    public String type() {
        return "setSimTimeSpeed";
    }

    @Override
    public String handle(Map<String, Object> message, StarAxisGameRuntime runtime) {
        try {
            Object scaleObj = message.get("scale");
            if (scaleObj == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"missing_scale\"}";
            }

            double scale;
            if (scaleObj instanceof Number) {
                scale = ((Number) scaleObj).doubleValue();
            } else {
                scale = Double.parseDouble(String.valueOf(scaleObj));
            }

            if (scale < 0.1 || scale > 10.0) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"invalid_scale\"}";
            }

            double before = 1.0;
            try {
                before = runtime.getWorldStateForSimOnly().time.timeScale;
            } catch (Exception ignored) {
            }

            runtime.getCommandBusForSimOnly().submit(new staraxis.game.command.SetTimeScaleCommand(scale));

            GameLog.log("cmd setSimTimeSpeed queued scale=" + scale + " beforeTimeScale=" + before);

            return "{\"type\":\"command_response\",\"ok\":true,\"command\":\"setSimTimeSpeed\",\"scale\":" + scale
                    + "}";
        } catch (Exception e) {
            return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"command_failed\"}";
        }
    }
}
