package staraxis.game.command;

/**
 * CommandResult
 *
 * @description
 *              命令执行结果（CommandResult），由 CommandBus 在执行命令后记录，
 *              供 UI/外部模块读取做命令结果反馈（G1.2）喵。
 *
 *              约定：
 *              - success=true 表示命令执行成功，failureCode 为 null。
 *              - success=false 表示命令执行失败，failureCode 为失败原因错误码，
 *                与命令处理器抛出的 IllegalArgumentException 消息一致（如
 *                ship_too_far_from_planet、planet_already_owned）喵。
 *              - tick 为命令执行时的模拟 tick（worldState.time.simulationTick），
 *                供 UI 判断结果的新旧喵。
 *
 * @api
 *      - CommandResult.success(String commandType, long tick): 构造成功结果
 *      - CommandResult.failure(String commandType, String failureCode, long tick):
 *      构造失败结果
 *      - String commandType(): 命令类型标识
 *      - boolean success(): 是否执行成功
 *      - String failureCode(): 失败原因错误码（成功时为 null）
 *      - long tick(): 执行时的模拟 tick
 *
 * @important_notes
 *                  - 结果对象为不可变 record，可在线程间安全传递喵。
 *                  - game 模块不感知 UI 文案，错误码到中文的映射由 UI 层负责喵。
 */
public record CommandResult(String commandType, boolean success, String failureCode, long tick) {

    /**
     * 构造成功结果喵。
     *
     * @param commandType 命令类型标识
     * @param tick        执行时的模拟 tick
     * @return 成功结果
     */
    public static CommandResult success(String commandType, long tick) {
        return new CommandResult(commandType, true, null, tick);
    }

    /**
     * 构造失败结果喵。
     *
     * @param commandType 命令类型标识
     * @param failureCode 失败原因错误码（来自命令处理器抛出的异常）
     * @param tick        执行时的模拟 tick
     * @return 失败结果
     */
    public static CommandResult failure(String commandType, String failureCode, long tick) {
        return new CommandResult(commandType, false, failureCode, tick);
    }
}
