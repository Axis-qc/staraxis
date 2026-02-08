package staraxis.game.command;

/**
 * Command
 *
 * @description
 *              游戏命令基类，所有需要在模拟 tick 中处理的命令都应继承此类。
 *
 *              作用：
 *              - 定义命令的基本契约，包含命令类型标识。
 *              - 为命令队列提供统一的类型，便于 CommandBus 管理和执行。
 *
 * @usage
 *        - 创建具体命令类继承 Command：
 *        - public class SetPlayerTimeStepCommand extends Command { ... }
 *        - 通过 CommandBus 提交命令到队列：
 *        - commandBus.submit(new SetPlayerTimeStepCommand(mps));
 *
 * @provides
 *           - **命令类型标识**: type() 方法返回命令类型字符串。
 *           - **命令基类**: 为所有具体命令提供统一的继承体系。
 *
 * @api
 *      - Command(String type): 构造函数，指定命令类型。
 *      - String type(): 获取命令类型标识符。
 *
 * @important_notes
 *                  - 命令应该是不可变的，避免在执行过程中被修改。
 *                  - 命令类型应该与前端发送的命令类型保持一致。
 *                  - 命令执行应该在模拟 tick 的 PrepareTick 阶段进行，确保时间同步。
 */
public abstract class Command {

    private final String type;

    /**
     * 构造函数
     * 
     * @param type 命令类型标识符，必须与前端发送的 type 字段匹配
     */
    protected Command(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("command_type_required");
        }
        this.type = type;
    }

    /**
     * 获取命令类型标识符
     * 
     * @return 命令类型字符串
     */
    public String type() {
        return type;
    }
}