package staraxis.game.command;

import staraxis.game.state.WorldState;

/**
 * SetSystemTimeScaleHandler
 *
 * @description
 *              SetSystemTimeScaleCommand 的处理器，在模拟 tick 内设置
 *              SimulationTime.timeScale 喵。
 *
 *              作用：
 *              - 将系统控制的倍率写入 WorldState.time.timeScale 喵。
 *              - 不影响玩家选择的推进档位，但会乘积影响最终推进速度喵。
 */
public class SetSystemTimeScaleHandler implements CommandHandler<SetSystemTimeScaleCommand> {

    @Override
    public void handle(SetSystemTimeScaleCommand command, WorldState worldState, double dtGameHours) {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        if (worldState == null || worldState.time == null) {
            throw new IllegalArgumentException("world_state_required");
        }

        double scale = command.getSystemScale();
        // 系统倍率限制，防止极端值导致模拟崩溃喵
        if (scale < 0.01 || scale > 100.0) {
            throw new IllegalArgumentException("invalid_system_scale");
        }

        worldState.time.timeScale = scale;
        worldState.markRealtimeDirty();
    }
}
