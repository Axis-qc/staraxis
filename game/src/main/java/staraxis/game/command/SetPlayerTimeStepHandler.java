package staraxis.game.command;

import staraxis.game.state.WorldState;

/**
 * SetPlayerTimeStepHandler
 *
 * @description
 *              SetPlayerTimeStepCommand 的处理器，在模拟 tick 内设置
 *              SimulationTime.playerTimeStep 喵。
 *
 *              作用：
 *              - 将命令携带的 minutesPerSecond 写入 WorldState.time.playerTimeStep 喵。
 *              - 使后续 tick 的 SimulationClock.prepareTick() 使用新的玩家档位推进时间喵。
 */
public class SetPlayerTimeStepHandler implements CommandHandler<SetPlayerTimeStepCommand> {

    @Override
    public void handle(SetPlayerTimeStepCommand command, WorldState worldState, double dtGameHours) {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        if (worldState == null || worldState.time == null) {
            throw new IllegalArgumentException("world_state_required");
        }

        double mps = command.getMinutesPerSecond();
        if (mps < 1.0 || mps > 1440.0) {
            throw new IllegalArgumentException("invalid_player_time_step");
        }

        worldState.time.playerTimeStep = mps;
    }
}
