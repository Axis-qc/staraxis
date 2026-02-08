package staraxis.game.command;

/**
 * SetSystemTimeScaleCommand
 *
 * @description
 *              设置系统时间倍率（timeScale）的内部命令喵。
 *              该倍率由系统控制，用于模拟性能调整、战斗加速、或者系统级的慢动作效果喵。
 *
 * @usage
 *        仅供后端内部系统调用，不暴露给前端 WebSocket 指令喵。
 *        runtime.getCommandBusForSimOnly().submit(new
 *        SetSystemTimeScaleCommand(0.5));
 */
public class SetSystemTimeScaleCommand extends Command {

    private final double systemScale;

    public SetSystemTimeScaleCommand(double systemScale) {
        super("setSystemTimeScale");
        this.systemScale = systemScale;
    }

    public double getSystemScale() {
        return systemScale;
    }
}
