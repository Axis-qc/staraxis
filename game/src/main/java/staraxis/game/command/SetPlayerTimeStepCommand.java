package staraxis.game.command;

/**
 * SetPlayerTimeStepCommand
 *
 * @description
 *              设置玩家时间推进速度的游戏命令喵。
 *
 *              新比例系统口径：
 *              - 参数 gameSecondsPerRealSecond：现实 1 秒对应推进的游戏秒数喵。
 *              - 例如：1.0 表示现实 1 秒推进游戏 1 秒（1:1）喵。
 *              - 例如：60.0 表示现实 1 秒推进游戏 60 秒（=1 游戏分钟）喵。
 *              - 例如：3600.0 表示现实 1 秒推进游戏 3600 秒（=1 游戏小时）喵。
 *
 *              权限规则：
 *              - SINGLE_PLAYER (单人) / MULTI_PLAYER (多人)：允许设置喵。
 *              - SERVER (服务器)：禁止设置，将抛出权限错误喵。
 */
public class SetPlayerTimeStepCommand extends Command {

    private final double gameSecondsPerRealSecond;

    public SetPlayerTimeStepCommand(double gameSecondsPerRealSecond) {
        super("setPlayerTimeStep");
        this.gameSecondsPerRealSecond = gameSecondsPerRealSecond;
    }

    public double getGameSecondsPerRealSecond() {
        return gameSecondsPerRealSecond;
    }
}
