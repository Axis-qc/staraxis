package staraxis.game.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import staraxis.game.astro.AstroData;
import staraxis.game.sim.SimulationTime;
import staraxis.game.state.WorldState;

/**
 * CommandBusResultTest（CommandBus 命令结果跟踪测试，G1.2）喵。
 *
 * 覆盖：
 * - 成功命令记录成功结果（commandType + tick）喵
 * - 失败命令记录失败结果（错误码 + tick）喵
 * - pollResults 消费后清空，getLastResults 保留快照喵
 * - 失败命令不阻塞后续命令执行（不破坏 submit / executeCommands 链路）喵
 * - 结果队列有界（超容量丢弃最旧结果）喵
 * - 非 IllegalArgumentException 异常统一归为 command_failed 喵
 */
class CommandBusResultTest {

    /** 测试命令：可配置执行时抛出的错误码或成功喵 */
    private static final class ProbeCommand extends Command {

        private final boolean fail;
        private final String failureCode;

        ProbeCommand(String type, boolean fail, String failureCode) {
            super(type);
            this.fail = fail;
            this.failureCode = failureCode;
        }
    }

    /** 测试处理器：fail=true 时抛出 IllegalArgumentException（消息即错误码）喵 */
    private static final class ProbeHandler implements CommandHandler<ProbeCommand> {
        @Override
        public void handle(ProbeCommand command, WorldState worldState, double dtGameHours) {
            if (command.fail) {
                throw new IllegalArgumentException(command.failureCode);
            }
        }
    }

    private CommandBus bus;
    private WorldState world;

    @BeforeEach
    void setUp() {
        bus = new CommandBus();
        bus.register(ProbeCommand.class, new ProbeHandler());
        SimulationTime time = new SimulationTime();
        time.simulationTick = 7L;
        world = new WorldState(time, 1000, new AstroData(List.of()));
    }

    @Test
    void successCommandRecordsSuccessResult() {
        bus.submit(new ProbeCommand("probe_ok", false, null));
        bus.executeCommands(world, 0);

        List<CommandResult> results = bus.pollResults();
        assertEquals(1, results.size());
        CommandResult r = results.get(0);
        assertTrue(r.success());
        assertEquals("probe_ok", r.commandType());
        assertEquals(7L, r.tick());
        assertNull(r.failureCode());
    }

    @Test
    void failedCommandRecordsFailureResultWithCode() {
        bus.submit(new ProbeCommand("probe_fail", true, "ship_too_far_from_planet"));
        bus.executeCommands(world, 0);

        List<CommandResult> results = bus.pollResults();
        assertEquals(1, results.size());
        CommandResult r = results.get(0);
        assertFalse(r.success());
        assertEquals("probe_fail", r.commandType());
        assertEquals("ship_too_far_from_planet", r.failureCode());
        assertEquals(7L, r.tick());
    }

    @Test
    void pollResultsDrainsButGetLastResultsKeepsSnapshot() {
        bus.submit(new ProbeCommand("a", true, "planet_already_owned"));
        bus.executeCommands(world, 0);

        // getLastResults 不消费，保留快照喵
        assertEquals(1, bus.getLastResults().size());
        assertEquals(1, bus.getLastResults().size());

        // pollResults 消费后清空喵
        assertEquals(1, bus.pollResults().size());
        assertTrue(bus.pollResults().isEmpty(), "pollResults 消费后应清空");
    }

    @Test
    void failedCommandDoesNotBlockFollowingCommands() {
        bus.submit(new ProbeCommand("first_fail", true, "invalid_colonization_parameters"));
        bus.submit(new ProbeCommand("second_ok", false, null));
        bus.executeCommands(world, 0);

        List<CommandResult> results = bus.pollResults();
        assertEquals(2, results.size());
        assertFalse(results.get(0).success());
        assertEquals("invalid_colonization_parameters", results.get(0).failureCode());
        assertTrue(results.get(1).success());
        // 既有语义：totalProcessed 只统计执行成功的命令，失败命令不计数喵
        assertEquals(1, bus.getTotalProcessed());
    }

    @Test
    void resultQueueIsBoundedAndDropsOldest() {
        // 提交超过容量上限的命令，结果队列只保留最近 MAX_RESULTS 条，丢弃最旧喵
        int total = CommandBus.MAX_RESULTS + 72;
        for (int i = 0; i < total; i++) {
            bus.submit(new ProbeCommand("c" + i, true, "code_" + i));
        }
        bus.executeCommands(world, 0);

        List<CommandResult> results = bus.pollResults();
        assertEquals(CommandBus.MAX_RESULTS, results.size());
        assertEquals("c72", results.get(0).commandType());
        assertEquals("c" + (total - 1), results.get(results.size() - 1).commandType());
    }

    @Test
    void nonIllegalArgumentExceptionFailureUsesGenericCode() {
        // 未注册处理器的命令：IllegalStateException 统一归为 command_failed 喵
        CommandBus bare = new CommandBus();
        bare.submit(new ProbeCommand("unregistered", false, null));
        bare.executeCommands(world, 0);

        List<CommandResult> results = bare.pollResults();
        assertEquals(1, results.size());
        assertFalse(results.get(0).success());
        assertEquals("command_failed", results.get(0).failureCode());
        assertEquals("unregistered", results.get(0).commandType());
    }
}
