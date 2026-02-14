package staraxis.webnet.command;

import staraxis.game.StarAxisGameRuntime;

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
 *           - **命令提交**: 提交 SetPlayerTimeStepCommand 到 game 层的命令队列
 *
 * @api
 *      - 入参字段：
 *      - minutesPerSecond: number（double），建议档位：1, 5, 10, 30, 60, 720, 1440 喵
 *      - 返回：统一 command_response：
 *      {"type":"command_response","ok":true,"command":"setSimTimeSpeed","minutesPerSecond":...}
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
            // 兼容旧字段名 scale，但语义已变为每秒推进的游戏分钟数喵
            Object scaleObj = message.get("scale");
            if (scaleObj == null) {
                scaleObj = message.get("minutesPerSecond");
            }
            if (scaleObj == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"missing_speed_value\"}";
            }

            double mps;
            if (scaleObj instanceof Number) {
                mps = ((Number) scaleObj).doubleValue();
            } else {
                mps = Double.parseDouble(String.valueOf(scaleObj));
            }

            // 校验玩家档位范围：1分钟/s 到 1440分钟(1日)/s 喵
            if (mps < 1.0 || mps > 1440.0) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"invalid_time_step\"}";
            }

            double currentStep = 1.0;
            try {
                currentStep = runtime.getWorldStateForSimOnly().time.playerTimeStep;
            } catch (Exception ignored) {
            }

            runtime.getCommandBusForSimOnly().submit(new staraxis.game.command.SetPlayerTimeStepCommand(mps));

            staraxis.webnet.core.WebNetLog.log("cmd setSimTimeSpeed queued mps=" + mps + " beforeStep=" + currentStep);

            return "{\"type\":\"command_response\",\"ok\":true,\"command\":\"setSimTimeSpeed\",\"minutesPerSecond\":"
                    + mps
                    + "}";
        } catch (Exception e) {
            return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"command_failed\"}";
        }
    }
}
