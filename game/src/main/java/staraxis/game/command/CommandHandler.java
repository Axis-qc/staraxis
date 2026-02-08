package staraxis.game.command;

import staraxis.game.state.WorldState;

/**
 * CommandHandler
 *
 * @description
 *              游戏命令处理器接口，用于在模拟 tick 中执行具体的命令逻辑。
 *
 *              作用：
 *              - 定义命令处理的标准契约，将命令执行逻辑与命令类型解耦。
 *              - 提供对 WorldState 的访问权限，允许命令修改游戏状态。
 *
 * @usage
 *        - 实现具体命令处理器：
 *        - public class SetPlayerTimeStepHandler implements
 *        CommandHandler<SetPlayerTimeStepCommand> { ... }
 *        - 在 CommandBus 中注册处理器：
 *        - commandBus.register(SetPlayerTimeStepCommand.class, new
 *        SetPlayerTimeStepHandler());
 *
 * @provides
 *           - **命令处理契约**: handle() 方法定义了命令执行的标准接口。
 *           - **类型安全**: 通过泛型确保处理器与命令类型的匹配。
 *
 * @api
 *      - void handle(T command, WorldState worldState, double dtGameHours):
 *      - command: 要执行的命令实例。
 *      - worldState: 当前世界状态，可直接修改。
 *      - dtGameHours: 当前 tick 的游戏小时数，用于时间相关的计算。
 *
 * @important_notes
 *                  - 命令处理器应该是无状态的，避免在处理器中保存状态。
 *                  - 命令执行应该在 PrepareTick 阶段完成，确保时间同步。
 *                  - 避免在 handle() 方法中执行耗时操作，以免阻塞模拟线程。
 *                  - 命令执行失败应该抛出异常，由 CommandBus 统一处理。
 *
 * @param <T> 命令类型，必须继承自 Command
 */
public interface CommandHandler<T extends Command> {

    /**
     * 处理命令
     * 
     * @param command     要执行的命令实例
     * @param worldState  当前世界状态，可直接修改
     * @param dtGameHours 当前 tick 的游戏小时数
     * @throws Exception 命令执行失败时抛出异常
     */
    void handle(T command, WorldState worldState, double dtGameHours) throws Exception;
}