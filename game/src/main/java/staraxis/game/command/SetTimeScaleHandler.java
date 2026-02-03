package staraxis.game.command;

import staraxis.game.state.WorldState;

/**
 * SetTimeScaleHandler
 *
 * @description
 *              SetTimeScaleCommand 的处理器，在模拟 tick 内设置 SimulationTime.timeScale。
 *
 *              作用：
 *              - 将命令携带的 scale 写入 WorldState.time.timeScale。
 *              - 使后续 tick 的 SimulationClock.prepareTick() 使用新的倍率推进时间。
 *
 * @usage
 *        - 在 StarAxisGameRuntime 构造/初始化阶段注册：
 *        - commandBus.register(SetTimeScaleCommand.class, new
 *        SetTimeScaleHandler());
 *
 * @provides
 *           - **时间倍率写入**: worldState.time.timeScale
 *
 * @api
 *      - handle(SetTimeScaleCommand command, WorldState worldState, double
 *      dtGameHours)
 *
 * @important_notes
 *                  - dtGameHours 为当前 tick 的游戏小时数，本命令不依赖该值。
 *                  - 这里做兜底范围校验（0.1~10.0），避免异常倍率导致模拟不稳定。
 */
public class SetTimeScaleHandler implements CommandHandler<SetTimeScaleCommand> {

    @Override
    public void handle(SetTimeScaleCommand command, WorldState worldState, double dtGameHours) {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        if (worldState == null || worldState.time == null) {
            throw new IllegalArgumentException("world_state_required");
        }

        double scale = command.getScale();
        if (scale < 0.1 || scale > 10.0) {
            throw new IllegalArgumentException("invalid_scale");
        }

        worldState.time.timeScale = scale;
    }
}
