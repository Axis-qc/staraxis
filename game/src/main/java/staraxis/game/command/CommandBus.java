package staraxis.game.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import staraxis.game.state.WorldState;

/**
 * CommandBus
 *
 * @description
 *              游戏命令总线，负责命令的提交、排队和执行。是命令系统的核心调度器。
 *
 *              作用：
 *              - 维护命令队列，支持多线程安全的命令提交。
 *              - 管理命令处理器注册表，实现命令类型到处理器的映射。
 *              - 在模拟 tick 中批量执行命令，确保命令执行与时间推进同步。
 *
 * @usage
 *        - 在 StarAxisGameRuntime 中创建实例：
 *        - commandBus = new CommandBus();
 *        - 注册命令处理器：
 *        - commandBus.register(SetPlayerTimeStepCommand.class, new
 *        SetPlayerTimeStepHandler());
 *        - 提交命令（通常由 webnet 层调用）：
 *        - commandBus.submit(new SetPlayerTimeStepCommand(minutesPerSecond));
 *        - 在 update() 方法中执行命令：
 *        - commandBus.executeCommands(worldState, dtGameHours);
 *
 * @provides
 *           - **命令提交**: submit() 方法支持多线程安全的命令提交。
 *           - **处理器注册**: register() 方法注册命令处理器。
 *           - **批量执行**: executeCommands() 方法在模拟 tick 中执行所有待处理命令。
 *           - **结果跟踪**: 每次命令执行后记录成功/失败结果，供 UI 轮询反馈（G1.2）喵。
 *           - **统计信息**: 提供命令队列长度和执行统计。
 *
 * @api
 *      - CommandBus(): 构造函数，初始化命令队列和处理器注册表。
 *      - void register(Class<T> commandClass, CommandHandler<T> handler):
 *      注册命令处理器。
 *      - void submit(Command command): 提交命令到队列。
 *      - void executeCommands(WorldState worldState, double dtGameHours):
 *      执行所有待处理命令。
 *      - List<CommandResult> pollResults(): 轮询并清空待处理命令结果（UI 每帧调用）喵。
 *      - List<CommandResult> getLastResults(): 获取当前结果队列快照（不消费）喵。
 *      - int getQueueSize(): 获取当前队列长度。
 *      - long getTotalProcessed(): 获取已处理的命令总数。
 *
 * @important_notes
 *                  - 使用 ConcurrentLinkedQueue 保证多线程安全的命令提交。
 *                  - 使用 ConcurrentHashMap 保证处理器注册表的线程安全。
 *                  - 命令执行是顺序的，按照提交顺序执行。
 *                  - 命令执行失败会记录到 System.err 但不会中断其他命令的执行。
 *                  - 命令队列会在每个 tick 开始时清空，避免命令堆积。
 *                  - 命令结果队列有界（{@link #MAX_RESULTS}），超容量丢弃最旧结果喵。
 *                  - game 模块保持纯粹，不依赖 webnet 或其他外部模块。
 */
public class CommandBus {

    /**
     * 命令结果队列的最大容量（有界，避免无限增长）喵。
     * 超出容量时丢弃最旧结果，仅保留最近的结果供 UI 反馈喵。
     */
    public static final int MAX_RESULTS = 128;

    private final ConcurrentLinkedQueue<Command> commandQueue = new ConcurrentLinkedQueue<>();
    private final Map<Class<? extends Command>, CommandHandler<?>> handlers = new ConcurrentHashMap<>();
    private final AtomicLong totalProcessed = new AtomicLong(0);

    /** 命令执行结果队列（有界 FIFO，UI 通过 pollResults 轮询消费）喵 */
    private final ConcurrentLinkedQueue<CommandResult> resultQueue = new ConcurrentLinkedQueue<>();

    /**
     * 注册命令处理器
     * 
     * @param commandClass 命令类型
     * @param handler      命令处理器
     * @param <T>          命令类型
     * @throws IllegalArgumentException 如果参数为 null
     */
    public <T extends Command> void register(Class<T> commandClass, CommandHandler<T> handler) {
        if (commandClass == null) {
            throw new IllegalArgumentException("command_class_required");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler_required");
        }
        handlers.put(commandClass, handler);
    }

    /**
     * 提交命令到队列
     * 
     * @param command 要提交的命令
     * @throws IllegalArgumentException 如果命令为 null
     */
    public void submit(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        commandQueue.offer(command);
    }

    /**
     * 立即同步执行单个命令（不入队列）喵。
     *
     * 用于启动阶段等需要同步结果的场景（如 LoadWorldCommand、JoinGameCommand），
     * 避免等待下一个 tick 的 executeCommands() 执行喵。
     *
     * @param command     要执行的命令
     * @param worldState  当前世界状态
     * @param dtGameHours 当前 tick 的游戏小时数
     * @throws IllegalStateException 如果未注册对应类型的处理器
     */
    @SuppressWarnings("unchecked")
    public <T extends Command> void executeImmediately(T command, WorldState worldState, double dtGameHours) {
        if (command == null) {
            throw new IllegalArgumentException("command_required");
        }
        long tick = worldState != null ? worldState.time.simulationTick : 0L;
        try {
            executeCommand(command, worldState, dtGameHours);
            totalProcessed.incrementAndGet();
            recordResult(CommandResult.success(command.type(), tick));
        } catch (Exception e) {
            recordResult(CommandResult.failure(command.type(), extractFailureCode(e), tick));
            throw new RuntimeException("Command execution failed: " + command.type(), e);
        }
    }

    /**
     * 执行所有待处理命令
     * 
     * @param worldState  当前世界状态
     * @param dtGameHours 当前 tick 的游戏小时数
     */
    public void executeCommands(WorldState worldState, double dtGameHours) {
        Command command;
        while ((command = commandQueue.poll()) != null) {
            long tick = worldState != null ? worldState.time.simulationTick : 0L;
            try {
                executeCommand(command, worldState, dtGameHours);
                totalProcessed.incrementAndGet();
                recordResult(CommandResult.success(command.type(), tick));
            } catch (Exception e) {
                System.err.println("Command execution failed: " + command.type() + " - " + String.valueOf(e));
                recordResult(CommandResult.failure(command.type(), extractFailureCode(e), tick));
            }
        }
    }

    /**
     * 执行单个命令
     * 
     * @param command     要执行的命令
     * @param worldState  当前世界状态
     * @param dtGameHours 当前 tick 的游戏小时数
     * @throws Exception 如果命令执行失败
     */
    @SuppressWarnings("unchecked")
    private void executeCommand(Command command, WorldState worldState, double dtGameHours) throws Exception {
        CommandHandler<Command> handler = (CommandHandler<Command>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for command: " + command.type());
        }
        handler.handle(command, worldState, dtGameHours);
    }

    /**
     * 轮询并清空待处理命令结果（供 UI 每帧读取，消费后队列清空）喵。
     *
     * @return 自上次调用以来新增的命令结果列表（按执行顺序）
     */
    public List<CommandResult> pollResults() {
        List<CommandResult> drained = new ArrayList<>();
        CommandResult result;
        while ((result = resultQueue.poll()) != null) {
            drained.add(result);
        }
        return drained;
    }

    /**
     * 获取当前结果队列快照（不消费，供诊断/调试使用）喵。
     *
     * @return 结果队列的不可变副本
     */
    public List<CommandResult> getLastResults() {
        return new ArrayList<>(resultQueue);
    }

    /**
     * 记录命令执行结果到有界队列（容量超限时丢弃最旧结果）喵。
     *
     * @param result 命令执行结果
     */
    private void recordResult(CommandResult result) {
        resultQueue.offer(result);
        while (resultQueue.size() > MAX_RESULTS) {
            resultQueue.poll();
        }
    }

    /**
     * 提取失败原因错误码（命令处理器抛出的 IllegalArgumentException 消息即错误码）喵。
     * 非 IllegalArgumentException（如缺少处理器等系统错误）统一归为 command_failed 喵。
     *
     * @param t 命令执行抛出的异常
     * @return 错误码字符串，保证非空
     */
    private static String extractFailureCode(Throwable t) {
        if (t instanceof IllegalArgumentException iae
                && iae.getMessage() != null && !iae.getMessage().isBlank()) {
            return iae.getMessage();
        }
        return "command_failed";
    }

    /**
     * 获取当前队列长度
     * 
     * @return 队列中待处理的命令数量
     */
    public int getQueueSize() {
        return commandQueue.size();
    }

    /**
     * 获取已处理的命令总数
     * 
     * @return 已处理的命令总数
     */
    public long getTotalProcessed() {
        return totalProcessed.get();
    }
}