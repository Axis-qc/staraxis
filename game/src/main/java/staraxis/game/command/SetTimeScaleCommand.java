package staraxis.game.command;

/**
 * SetTimeScaleCommand
 *
 * @description
 *              设置模拟时间倍率（timeScale）的游戏命令。
 *
 *              作用：
 *              - 表示“调整时间推进速度”的意图。
 *              - 由 CommandBus 在模拟 tick 的 PrepareTick 阶段统一执行，从而确保时序一致。
 *
 * @usage
 *        - webnet 层收到前端 setSimTimeSpeed 指令后：
 *        - runtime.getCommandBusForSimOnly().submit(new
 *        SetTimeScaleCommand(scale));
 *        - 在 StarAxisGameRuntime.update 中：
 *        - commandBus.executeCommands(worldState, dtGameHours);
 *
 * @provides
 *           - **时间倍率调整意图**: scale 字段。
 *
 * @api
 *      - new SetTimeScaleCommand(double scale)
 *      - double getScale(): 获取倍率
 *
 * @important_notes
 *                  - scale 建议由上层做档位约束（如 0.25/0.5/0.75/1/2/3/4），game 侧可再做兜底校验。
 *                  - 命令对象应当不可变。
 */
public class SetTimeScaleCommand extends Command {

    private final double scale;

    public SetTimeScaleCommand(double scale) {
        super("setTimeScale");
        this.scale = scale;
    }

    public double getScale() {
        return scale;
    }
}
