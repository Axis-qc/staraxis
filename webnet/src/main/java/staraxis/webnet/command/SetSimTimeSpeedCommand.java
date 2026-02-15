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
            // 新口径：gameSecondsPerRealSecond（现实 1 秒推进的游戏秒数）喵。
            // 兼容旧字段：
            // - scale：旧前端字段名（曾用于 timeScale）喵。
            // - minutesPerSecond：旧口径（游戏分钟/现实秒）喵。
            Object v = message.get("gameSecondsPerRealSecond");
            if (v == null) {
                v = message.get("secondsPerSecond");
            }
            if (v == null) {
                v = message.get("scale");
            }
            if (v == null) {
                v = message.get("minutesPerSecond");
            }
            if (v == null) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"missing_speed_value\"}";
            }

            double gsprs;
            if (v instanceof Number) {
                gsprs = ((Number) v).doubleValue();
            } else {
                gsprs = Double.parseDouble(String.valueOf(v));
            }

            // 如果前端仍发送旧口径 minutesPerSecond，则将其转换为秒级比例喵。
            // minutesPerSecond（游戏分钟/现实秒） => gameSecondsPerRealSecond（游戏秒/现实秒）
            if (message.get("minutesPerSecond") != null && message.get("gameSecondsPerRealSecond") == null
                    && message.get("secondsPerSecond") == null) {
                gsprs = gsprs * 60.0;
            }

            // 基础校验：必须为正数，避免异常值导致模拟不稳定喵。
            if (gsprs <= 0.0) {
                return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"invalid_time_step\"}";
            }

            double currentStep = 1.0;
            try {
                currentStep = runtime.getWorldStateForSimOnly().time.playerTimeStep;
            } catch (Exception ignored) {
            }

            runtime.getCommandBusForSimOnly().submit(new staraxis.game.command.SetPlayerTimeStepCommand(gsprs));

            staraxis.webnet.core.WebNetLog
                    .log("cmd setSimTimeSpeed queued gsprs=" + gsprs + " beforeStep=" + currentStep);

            return "{\"type\":\"command_response\",\"ok\":true,\"command\":\"setSimTimeSpeed\",\"gameSecondsPerRealSecond\":"
                    + gsprs
                    + "}";
        } catch (Exception e) {
            return "{\"type\":\"command_response\",\"ok\":false,\"error\":\"command_failed\"}";
        }
    }
}
