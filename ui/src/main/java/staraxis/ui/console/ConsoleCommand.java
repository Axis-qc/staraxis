package staraxis.ui.console;

/**
 * 控制台命令接口。
 *
 * 设计要点：
 * - 命令只做“触发与调试”，不直接改写核心模拟状态。
 * - 输出通过 ConsoleOutput 统一写入控制台视图。
 */
public interface ConsoleCommand {

    String name();

    String help();

    void execute(String[] args, ConsoleOutput out);
}
