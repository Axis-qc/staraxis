package staraxis.game.command;

import staraxis.game.sim.SimulationClock;
import staraxis.game.state.WorldState;
import staraxis.game.world.WorldType;

/**
 * SetPlayerTimeStepHandler
 *
 * @description
 *              SetPlayerTimeStepCommand 的处理器，在模拟 tick 内设置时间推进比例喵。
 *
 *              作用：
 *              - 单人/多人世界：将 hoursPerSecond（现实 1 秒 -> 游戏小时数）转换为
 *              gameSecondsPerRealSecond（现实 1 秒 -> 游戏秒数）并写入
 *              SimulationTime.gameSecondsPerRealSecond（推进比例字段）喵。
 *              - 服务器世界：禁止客户端设置，将抛出权限错误喵。
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

        WorldType wt = worldState.time.worldType;
        if (wt == WorldType.SERVER) {
            throw new IllegalArgumentException("forbidden_time_step_in_server_world");
        }

        double gsprs = command.getGameSecondsPerRealSecond();
        if (gsprs <= 0.0) {
            throw new IllegalArgumentException("invalid_player_time_step");
        }

        worldState.time.gameSecondsPerRealSecond = gsprs;

        // 保留旧字段用于兼容 UI/协议展示喵。
        worldState.time.playerTimeStep = (gsprs / SimulationClock.SECONDS_PER_MINUTE);
    }
}
