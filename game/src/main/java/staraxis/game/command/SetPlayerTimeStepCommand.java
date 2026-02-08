package staraxis.game.command;

/**
 * SetPlayerTimeStepCommand
 *
 * @description
 *              设置玩家时间推进速度（playerTimeStep）的游戏命令喵。
 *              单位为：游戏分钟 / 现实秒喵。
 *
 *              可选档位：
 *              1 (1m/s), 5 (5m/s), 10 (10m/s), 30 (30m/s), 60 (1h/s), 720
 *              (12h/s), 1440 (1d/s) 喵。
 */
public class SetPlayerTimeStepCommand extends Command {

    private final double minutesPerSecond;

    public SetPlayerTimeStepCommand(double minutesPerSecond) {
        super("setPlayerTimeStep");
        this.minutesPerSecond = minutesPerSecond;
    }

    public double getMinutesPerSecond() {
        return minutesPerSecond;
    }
}
